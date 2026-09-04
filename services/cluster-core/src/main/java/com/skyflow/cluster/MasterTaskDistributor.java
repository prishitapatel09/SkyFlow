package com.skyflow.cluster;

import com.skyflow.cluster.grpc.TaskAck;
import com.skyflow.cluster.grpc.TaskAssignment;
import com.skyflow.cluster.grpc.TaskResult;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Master side of the master-worker split. Active only on the elected leader.
 *
 * <p>Dispatch picks the least-loaded live worker (the leader included, unless
 * {@code leader-executes-tasks} is false) and tracks every assignment with a deadline. Work is
 * recovered in three ways, which together are the "automatic failover" of this design:
 *
 * <ul>
 *   <li>the worker reports a failure -&gt; requeue,</li>
 *   <li>the worker stops answering heartbeats and {@link WorkerRegistry} declares it dead -&gt;
 *       everything it held is requeued at once,</li>
 *   <li>the deadline passes with no result (a slow worker, or a lost report) -&gt; requeue.</li>
 * </ul>
 *
 * <p>Because the pending queue is in-memory and not replicated, a task can run twice - once on a
 * worker presumed dead and once on its replacement. Handlers must be idempotent.
 */
public class MasterTaskDistributor implements RaftCoordinator.LeadershipListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MasterTaskDistributor.class);
    private static final long DISPATCH_INTERVAL_MILLIS = 100;

    private final ClusterProperties properties;
    private final RaftCoordinator raft;
    private final WorkerRegistry workerRegistry;
    private final PeerChannels peerChannels;
    private final WorkerTaskExecutor localExecutor;

    private final ConcurrentLinkedDeque<ClusterTask> pending = new ConcurrentLinkedDeque<>();
    private final Map<String, Dispatch> inFlight = new ConcurrentHashMap<>();

    private final ScheduledExecutorService dispatcher =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "cluster-master");
                thread.setDaemon(true);
                return thread;
            });

    public MasterTaskDistributor(ClusterProperties properties, RaftCoordinator raft,
                                 WorkerRegistry workerRegistry, PeerChannels peerChannels,
                                 WorkerTaskExecutor localExecutor) {
        this.properties = properties;
        this.raft = raft;
        this.workerRegistry = workerRegistry;
        this.peerChannels = peerChannels;
        this.localExecutor = localExecutor;
    }

    public void start() {
        workerRegistry.onWorkerDeath(this::reassignTasksOf);
        localExecutor.setLocalResultSink(this::onTaskResult);
        raft.addLeadershipListener(this);
        dispatcher.scheduleAtFixedRate(this::dispatchSafely,
                DISPATCH_INTERVAL_MILLIS, DISPATCH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Queues a task, but only while this node is the leader. Scheduled producers call this from
     * every replica; exactly one of them - the leader - actually enqueues, which is what keeps a
     * periodic sweep from running N times on an N-replica deployment.
     *
     * @return {@code true} if the task was queued
     */
    public boolean submitIfLeader(String type, String payloadJson) {
        if (!raft.isLeader()) {
            return false;
        }
        if (!localExecutor.hasHandler(type)) {
            log.warn("Refusing to queue task of unknown type {}", type);
            return false;
        }
        pending.addLast(ClusterTask.of(type, payloadJson));
        raft.observeTask();
        return true;
    }

    private void dispatchSafely() {
        try {
            if (!raft.isLeader()) {
                return;
            }
            expireOverdueTasks();
            dispatchPending();
        } catch (RuntimeException ex) {
            log.error("Dispatch cycle failed", ex);
        }
    }

    private void expireOverdueTasks() {
        long now = System.currentTimeMillis();
        for (Dispatch dispatch : List.copyOf(inFlight.values())) {
            if (now <= dispatch.deadlineMillis()) {
                continue;
            }
            if (inFlight.remove(dispatch.task().id()) == null) {
                continue; // a result arrived while we were iterating
            }
            workerRegistry.decrementInFlight(dispatch.workerId());
            log.warn("Task {} timed out on worker {}; reassigning", dispatch.task().id(), dispatch.workerId());
            requeue(dispatch.task());
        }
    }

    private void dispatchPending() {
        ClusterTask task;
        while ((task = pending.pollFirst()) != null) {
            Optional<String> target = chooseWorker();
            if (target.isEmpty()) {
                pending.addFirst(task); // no capacity anywhere; try again next tick
                return;
            }
            assign(task, target.get());
        }
    }

    /** Least-loaded worker with spare capacity, considering this node too. */
    private Optional<String> chooseWorker() {
        List<Candidate> candidates = new ArrayList<>();
        if (properties.isLeaderExecutesTasks()
                && localExecutor.inFlight() < localExecutor.capacity()) {
            candidates.add(new Candidate(raft.nodeId(), localExecutor.inFlight()));
        }
        for (ClusterStatus.WorkerSnapshot worker : workerRegistry.snapshot()) {
            if (worker.alive() && worker.inFlightTasks() < worker.capacity()) {
                candidates.add(new Candidate(worker.nodeId(), worker.inFlightTasks()));
            }
        }
        return candidates.stream()
                .min(Comparator.comparingInt(Candidate::load))
                .map(Candidate::nodeId);
    }

    private void assign(ClusterTask task, String workerId) {
        TaskAssignment assignment = TaskAssignment.newBuilder()
                .setTerm(raft.currentTerm())
                .setLeaderId(raft.nodeId())
                .setTaskId(task.id())
                .setType(task.type())
                .setPayloadJson(task.payloadJson())
                .setAttempt(task.attempt())
                .setDeadlineEpochMillis(System.currentTimeMillis() + properties.getTaskTimeout().toMillis())
                .build();

        boolean isSelf = workerId.equals(raft.nodeId());

        // Recorded as in-flight *before* it is handed over: a fast worker - especially this node
        // running the task itself - can report the result before the assignment call returns, and
        // a result for a task the master has not registered yet would be dropped and then time out.
        inFlight.put(task.id(), new Dispatch(task, workerId,
                System.currentTimeMillis() + properties.getTaskTimeout().toMillis()));
        if (!isSelf) {
            workerRegistry.incrementInFlight(workerId);
        }

        TaskAck ack;
        if (isSelf) {
            ack = localExecutor.accept(assignment);
        } else {
            try {
                ack = peerChannels.stub(workerId)
                        .withDeadlineAfter(properties.getRpcDeadline().toMillis(), TimeUnit.MILLISECONDS)
                        .assignTask(assignment);
            } catch (StatusRuntimeException ex) {
                peerChannels.reset(workerId);
                abandon(task, workerId, isSelf);
                workerRegistry.recordMiss(workerId);
                log.warn("Assignment of task {} to {} failed ({}); requeuing",
                        task.id(), workerId, ex.getStatus().getCode());
                requeue(task);
                return;
            }
        }

        if (!ack.getAccepted()) {
            log.debug("Worker {} refused task {}: {}", workerId, task.id(), ack.getReason());
            abandon(task, workerId, isSelf);
            pending.addFirst(task);
            return;
        }
        log.debug("Task {} ({}) attempt {} -> {}", task.id(), task.type(), task.attempt(), workerId);
    }

    /** Undoes the bookkeeping of an assignment that was never taken up. */
    private void abandon(ClusterTask task, String workerId, boolean isSelf) {
        inFlight.remove(task.id());
        if (!isSelf) {
            workerRegistry.decrementInFlight(workerId);
        }
    }

    /** Serves {@code ReportTaskResult}, and is also called directly when the leader ran the task. */
    public void onTaskResult(TaskResult result) {
        Dispatch dispatch = inFlight.remove(result.getTaskId());
        if (dispatch == null) {
            return; // already timed out and reassigned
        }
        workerRegistry.decrementInFlight(dispatch.workerId());

        if (result.getSuccess()) {
            log.debug("Task {} completed on {} in {}ms",
                    result.getTaskId(), result.getWorkerId(), result.getDurationMillis());
            return;
        }
        log.warn("Task {} failed on {}: {}", result.getTaskId(), result.getWorkerId(), result.getMessage());
        requeue(dispatch.task());
    }

    /** Recovers every task held by a worker that was just declared dead. */
    private void reassignTasksOf(String workerId) {
        List<ClusterTask> orphaned = inFlight.values().stream()
                .filter(dispatch -> dispatch.workerId().equals(workerId))
                .map(Dispatch::task)
                .toList();

        orphaned.forEach(task -> {
            inFlight.remove(task.id());
            requeue(task);
        });
        if (!orphaned.isEmpty()) {
            log.warn("Reassigned {} task(s) stranded on dead worker {}", orphaned.size(), workerId);
        }
    }

    private void requeue(ClusterTask task) {
        if (task.attempt() >= properties.getMaxTaskAttempts()) {
            log.error("Dropping task {} ({}) after {} attempts", task.id(), task.type(), task.attempt());
            return;
        }
        pending.addFirst(task.nextAttempt());
    }

    @Override
    public void onBecameLeader(long term) {
        // Assignments from a previous term are void; the work itself stays queued.
        inFlight.clear();
        log.info("Master role active for term {}: {} task(s) queued", term, pending.size());
    }

    @Override
    public void onLostLeadership(long term) {
        int dropped = pending.size() + inFlight.size();
        pending.clear();
        inFlight.clear();
        if (dropped > 0) {
            log.warn("No longer master for term {}; released {} queued task(s) to the new leader",
                    term, dropped);
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    @Override
    public void close() {
        dispatcher.shutdownNow();
    }

    private record Dispatch(ClusterTask task, String workerId, long deadlineMillis) {
    }

    private record Candidate(String nodeId, int load) {
    }
}
