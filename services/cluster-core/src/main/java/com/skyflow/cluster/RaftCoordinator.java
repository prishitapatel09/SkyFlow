package com.skyflow.cluster;

import com.skyflow.cluster.grpc.HeartbeatRequest;
import com.skyflow.cluster.grpc.HeartbeatResponse;
import com.skyflow.cluster.grpc.VoteRequest;
import com.skyflow.cluster.grpc.VoteResponse;
import com.skyflow.cluster.grpc.WorkerStatus;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Raft-inspired leader election across the replicas of one service.
 *
 * <p>It implements the parts of Raft that a task distributor actually needs - monotonic terms, one
 * vote per node per term, randomized election timeouts to avoid split votes, majority quorum, and
 * step-down on seeing a higher term - over two gRPC calls: {@code RequestVote} and
 * {@code Heartbeat}. It deliberately does <em>not</em> implement log replication: the queue of
 * pending tasks is not replicated, so a leader change can re-run an in-flight task. Every
 * {@link ClusterTaskHandler} is therefore expected to be idempotent.
 *
 * <p>The same {@code Heartbeat} RPC serves three purposes: followers learn the leader is alive
 * (and reset their election timers), the leader learns a follower is alive (failure detection),
 * and the response carries the follower's load so the leader can balance dispatch.
 */
public class RaftCoordinator implements AutoCloseable {

    /** Node-local load, reported to the leader on every heartbeat. */
    public interface LoadReporter {
        int inFlight();

        int capacity();
    }

    /** Notified when this node gains or loses leadership. */
    public interface LeadershipListener {
        void onBecameLeader(long term);

        void onLostLeadership(long term);
    }

    private static final Logger log = LoggerFactory.getLogger(RaftCoordinator.class);
    private static final long TICK_MILLIS = 50;

    private final ClusterProperties properties;
    private final PeerChannels peerChannels;
    private final WorkerRegistry workerRegistry;
    private final String nodeId;
    private final Random random = new Random();
    private final List<LeadershipListener> listeners = new ArrayList<>();
    private final AtomicLong lastTaskIndex = new AtomicLong();

    private final ScheduledExecutorService ticker =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "raft-ticker");
                thread.setDaemon(true);
                return thread;
            });
    private final ExecutorService rpcExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "raft-rpc");
        thread.setDaemon(true);
        return thread;
    });

    private volatile LoadReporter loadReporter = new LoadReporter() {
        @Override
        public int inFlight() {
            return 0;
        }

        @Override
        public int capacity() {
            return 0;
        }
    };

    // Persistent-ish state, guarded by `this`.
    private long currentTerm;
    private String votedFor;
    private NodeRole role = NodeRole.FOLLOWER;
    private String leaderId;
    private long electionDeadlineMillis;
    private long lastHeartbeatSentMillis;

    public RaftCoordinator(ClusterProperties properties, PeerChannels peerChannels,
                           WorkerRegistry workerRegistry) {
        this.properties = properties;
        this.peerChannels = peerChannels;
        this.workerRegistry = workerRegistry;
        this.nodeId = properties.nodeId();
    }

    public void setLoadReporter(LoadReporter loadReporter) {
        this.loadReporter = loadReporter;
    }

    public void addLeadershipListener(LeadershipListener listener) {
        listeners.add(listener);
    }

    public void start() {
        properties.otherPeers().forEach(workerRegistry::track);
        synchronized (this) {
            resetElectionDeadline();
        }
        log.info("Cluster node {} starting: {} peer(s), quorum {}",
                nodeId, properties.otherPeers().size(), properties.quorum());
        ticker.scheduleAtFixedRate(this::tickSafely, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException ex) {
            log.error("Coordination tick failed", ex);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean shouldHeartbeat = false;
        boolean shouldElect = false;

        synchronized (this) {
            if (role == NodeRole.LEADER) {
                if (now - lastHeartbeatSentMillis >= properties.getHeartbeatInterval().toMillis()) {
                    lastHeartbeatSentMillis = now;
                    shouldHeartbeat = true;
                }
            } else if (now >= electionDeadlineMillis) {
                shouldElect = true;
            }
        }

        if (shouldHeartbeat) {
            broadcastHeartbeats();
        } else if (shouldElect) {
            startElection();
        }
    }

    // ---------------------------------------------------------------- elections

    private void startElection() {
        long term;
        synchronized (this) {
            currentTerm++;
            term = currentTerm;
            role = NodeRole.CANDIDATE;
            votedFor = nodeId;
            leaderId = null;
            resetElectionDeadline();
        }

        List<String> peers = properties.otherPeers();
        log.info("Election timeout reached; standing for election in term {} ({} peer(s))",
                term, peers.size());

        AtomicInteger votes = new AtomicInteger(1); // vote for self
        if (votes.get() >= properties.quorum()) {
            becomeLeader(term);
            return;
        }

        VoteRequest request = VoteRequest.newBuilder()
                .setTerm(term)
                .setCandidateId(nodeId)
                .setLastTaskIndex(lastTaskIndex.get())
                .build();

        for (String peer : peers) {
            rpcExecutor.execute(() -> requestVote(peer, request, term, votes));
        }
    }

    private void requestVote(String peer, VoteRequest request, long term, AtomicInteger votes) {
        try {
            VoteResponse response = peerChannels.stub(peer)
                    .withDeadlineAfter(properties.getRpcDeadline().toMillis(), TimeUnit.MILLISECONDS)
                    .requestVote(request);

            if (response.getTerm() > term) {
                stepDown(response.getTerm(), null);
                return;
            }
            if (response.getVoteGranted() && votes.incrementAndGet() >= properties.quorum()) {
                becomeLeader(term);
            }
        } catch (StatusRuntimeException ex) {
            peerChannels.reset(peer);
            log.debug("Vote request to {} failed: {}", peer, ex.getStatus().getCode());
        }
    }

    private void becomeLeader(long term) {
        synchronized (this) {
            if (role != NodeRole.CANDIDATE || currentTerm != term) {
                return; // superseded while votes were in flight
            }
            role = NodeRole.LEADER;
            leaderId = nodeId;
            lastHeartbeatSentMillis = 0; // heartbeat on the next tick
        }
        log.info("Elected leader for term {}; taking over task distribution", term);
        workerRegistry.resetForNewTerm();
        listeners.forEach(listener -> listener.onBecameLeader(term));
    }

    /** Reverts to follower on seeing a newer term. */
    private void stepDown(long newTerm, String newLeaderId) {
        boolean lostLeadership;
        long previousTerm;
        synchronized (this) {
            previousTerm = currentTerm;
            if (newTerm < currentTerm) {
                return;
            }
            lostLeadership = role == NodeRole.LEADER;
            if (newTerm > currentTerm) {
                currentTerm = newTerm;
                votedFor = null;
            }
            role = NodeRole.FOLLOWER;
            leaderId = newLeaderId;
            resetElectionDeadline();
        }
        if (lostLeadership) {
            log.warn("Stepping down from leader of term {}: term {} observed", previousTerm, newTerm);
            listeners.forEach(listener -> listener.onLostLeadership(previousTerm));
        }
    }

    private void resetElectionDeadline() {
        long min = properties.getElectionTimeoutMin().toMillis();
        long max = Math.max(min + 1, properties.getElectionTimeoutMax().toMillis());
        electionDeadlineMillis = System.currentTimeMillis() + min + random.nextLong(max - min);
    }

    // ---------------------------------------------------------------- heartbeats

    private void broadcastHeartbeats() {
        HeartbeatRequest request;
        synchronized (this) {
            request = HeartbeatRequest.newBuilder()
                    .setTerm(currentTerm)
                    .setLeaderId(nodeId)
                    .setSentAtEpochMillis(System.currentTimeMillis())
                    .setLastTaskIndex(lastTaskIndex.get())
                    .build();
        }
        for (String peer : properties.otherPeers()) {
            rpcExecutor.execute(() -> heartbeat(peer, request));
        }
    }

    private void heartbeat(String peer, HeartbeatRequest request) {
        try {
            HeartbeatResponse response = peerChannels.stub(peer)
                    .withDeadlineAfter(properties.getRpcDeadline().toMillis(), TimeUnit.MILLISECONDS)
                    .heartbeat(request);

            if (response.getTerm() > request.getTerm()) {
                stepDown(response.getTerm(), null);
                return;
            }
            WorkerStatus status = response.getStatus();
            workerRegistry.recordAlive(peer,
                    status.getDraining() ? Integer.MAX_VALUE : status.getInFlightTasks(),
                    status.getCapacity());
        } catch (StatusRuntimeException ex) {
            peerChannels.reset(peer);
            workerRegistry.recordMiss(peer);
        }
    }

    // ---------------------------------------------------------------- RPC handlers

    /** Serves {@code RequestVote}. */
    public synchronized VoteResponse handleVoteRequest(VoteRequest request) {
        if (request.getTerm() < currentTerm) {
            return voteResponse(false);
        }
        if (request.getTerm() > currentTerm) {
            stepDownLocked(request.getTerm(), null);
        }

        boolean canVote = votedFor == null || votedFor.equals(request.getCandidateId());
        boolean candidateIsCurrent = request.getLastTaskIndex() >= lastTaskIndex.get();
        if (canVote && candidateIsCurrent) {
            votedFor = request.getCandidateId();
            resetElectionDeadline();
            log.info("Granting vote to {} for term {}", request.getCandidateId(), currentTerm);
            return voteResponse(true);
        }
        return voteResponse(false);
    }

    private VoteResponse voteResponse(boolean granted) {
        return VoteResponse.newBuilder()
                .setTerm(currentTerm)
                .setVoteGranted(granted)
                .setVoterId(nodeId)
                .build();
    }

    /** Serves {@code Heartbeat}. */
    public HeartbeatResponse handleHeartbeat(HeartbeatRequest request) {
        boolean accepted;
        long term;
        synchronized (this) {
            if (request.getTerm() < currentTerm) {
                accepted = false;
            } else {
                if (request.getTerm() > currentTerm || role != NodeRole.FOLLOWER) {
                    stepDownLocked(request.getTerm(), request.getLeaderId());
                }
                leaderId = request.getLeaderId();
                lastTaskIndex.accumulateAndGet(request.getLastTaskIndex(), Math::max);
                resetElectionDeadline();
                accepted = true;
            }
            term = currentTerm;
        }
        return HeartbeatResponse.newBuilder()
                .setTerm(term)
                .setAccepted(accepted)
                .setStatus(WorkerStatus.newBuilder()
                        .setNodeId(nodeId)
                        .setInFlightTasks(loadReporter.inFlight())
                        .setCapacity(loadReporter.capacity())
                        .build())
                .build();
    }

    private void stepDownLocked(long newTerm, String newLeaderId) {
        boolean lostLeadership = role == NodeRole.LEADER;
        long previousTerm = currentTerm;
        if (newTerm > currentTerm) {
            currentTerm = newTerm;
            votedFor = null;
        }
        role = NodeRole.FOLLOWER;
        leaderId = newLeaderId;
        if (lostLeadership) {
            log.warn("Another leader ({}) is active in term {}; stepping down", newLeaderId, newTerm);
            listeners.forEach(listener -> listener.onLostLeadership(previousTerm));
        }
    }

    // ---------------------------------------------------------------- accessors

    public synchronized boolean isLeader() {
        return role == NodeRole.LEADER;
    }

    public synchronized NodeRole role() {
        return role;
    }

    public synchronized long currentTerm() {
        return currentTerm;
    }

    public synchronized String leaderId() {
        return leaderId;
    }

    public String nodeId() {
        return nodeId;
    }

    /** Bumps the observed task counter, used as the election tie-breaker. */
    public long observeTask() {
        return lastTaskIndex.incrementAndGet();
    }

    public long lastTaskIndex() {
        return lastTaskIndex.get();
    }

    @Override
    public void close() {
        ticker.shutdownNow();
        rpcExecutor.shutdownNow();
    }
}
