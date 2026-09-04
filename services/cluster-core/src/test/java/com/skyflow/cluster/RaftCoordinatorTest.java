package com.skyflow.cluster;

import com.skyflow.cluster.grpc.HeartbeatRequest;
import com.skyflow.cluster.grpc.HeartbeatResponse;
import com.skyflow.cluster.grpc.VoteRequest;
import com.skyflow.cluster.grpc.VoteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RaftCoordinatorTest {

    private ClusterProperties properties;
    private PeerChannels peerChannels;
    private RaftCoordinator raft;

    @BeforeEach
    void setUp() {
        properties = new ClusterProperties();
        properties.setAdvertisedHost("node-a");
        properties.setGrpcPort(19090);
        properties.setElectionTimeoutMin(Duration.ofMillis(120));
        properties.setElectionTimeoutMax(Duration.ofMillis(200));
        properties.setHeartbeatInterval(Duration.ofMillis(50));

        peerChannels = new PeerChannels();
        raft = new RaftCoordinator(properties, peerChannels, new WorkerRegistry(properties));
    }

    @AfterEach
    void tearDown() {
        raft.close();
        peerChannels.close();
    }

    @Test
    @DisplayName("a single node with no peers elects itself, because quorum is 1")
    void singleNodeBecomesLeader() {
        assertThat(properties.quorum()).isEqualTo(1);
        assertThat(raft.role()).isEqualTo(NodeRole.FOLLOWER);

        raft.start();

        await().atMost(Duration.ofSeconds(2)).until(raft::isLeader);
        assertThat(raft.leaderId()).isEqualTo("node-a:19090");
        assertThat(raft.currentTerm()).isPositive();
    }

    @Test
    @DisplayName("a three node cluster needs two votes")
    void quorumIsAMajority() {
        properties.setPeers(List.of("node-a:19090", "node-b:19090", "node-c:19090"));

        assertThat(properties.otherPeers()).containsExactly("node-b:19090", "node-c:19090");
        assertThat(properties.quorum()).isEqualTo(2);
    }

    @Test
    @DisplayName("a vote is granted once per term, and never to a stale term")
    void votesOncePerTerm() {
        VoteResponse first = raft.handleVoteRequest(voteRequest(5, "node-b:19090"));
        assertThat(first.getVoteGranted()).isTrue();
        assertThat(first.getTerm()).isEqualTo(5);

        // A different candidate in the same term must be refused: two leaders per term is exactly
        // what the vote is there to prevent.
        VoteResponse second = raft.handleVoteRequest(voteRequest(5, "node-c:19090"));
        assertThat(second.getVoteGranted()).isFalse();

        // A repeat from the same candidate is idempotent, so a retried RPC is harmless.
        assertThat(raft.handleVoteRequest(voteRequest(5, "node-b:19090")).getVoteGranted()).isTrue();

        VoteResponse stale = raft.handleVoteRequest(voteRequest(4, "node-c:19090"));
        assertThat(stale.getVoteGranted()).isFalse();
        assertThat(stale.getTerm()).isEqualTo(5);
    }

    @Test
    @DisplayName("a candidate that has seen less work than the voter is refused")
    void refusesALessCurrentCandidate() {
        raft.observeTask();
        raft.observeTask();

        assertThat(raft.handleVoteRequest(VoteRequest.newBuilder()
                .setTerm(3)
                .setCandidateId("node-b:19090")
                .setLastTaskIndex(1)
                .build()).getVoteGranted()).isFalse();

        assertThat(raft.handleVoteRequest(VoteRequest.newBuilder()
                .setTerm(3)
                .setCandidateId("node-b:19090")
                .setLastTaskIndex(2)
                .build()).getVoteGranted()).isTrue();
    }

    @Test
    @DisplayName("a leader steps down when it hears from a newer term")
    void leaderStepsDownOnNewerTerm() {
        AtomicInteger lostLeadership = new AtomicInteger();
        raft.addLeadershipListener(new RaftCoordinator.LeadershipListener() {
            @Override
            public void onBecameLeader(long term) {
                // no-op
            }

            @Override
            public void onLostLeadership(long term) {
                lostLeadership.incrementAndGet();
            }
        });

        raft.start();
        await().atMost(Duration.ofSeconds(2)).until(raft::isLeader);
        long ourTerm = raft.currentTerm();

        HeartbeatResponse response = raft.handleHeartbeat(HeartbeatRequest.newBuilder()
                .setTerm(ourTerm + 1)
                .setLeaderId("node-b:19090")
                .setSentAtEpochMillis(System.currentTimeMillis())
                .build());

        assertThat(response.getAccepted()).isTrue();
        assertThat(raft.currentTerm()).isEqualTo(ourTerm + 1);
        assertThat(lostLeadership.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a heartbeat from a stale leader is rejected without changing the term")
    void rejectsStaleLeaderHeartbeat() {
        raft.handleVoteRequest(voteRequest(9, "node-b:19090"));

        HeartbeatResponse response = raft.handleHeartbeat(HeartbeatRequest.newBuilder()
                .setTerm(7)
                .setLeaderId("node-c:19090")
                .build());

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getTerm()).isEqualTo(9);
        assertThat(raft.currentTerm()).isEqualTo(9);
    }

    @Test
    @DisplayName("heartbeat responses carry this node's load, which is how the master balances")
    void heartbeatResponseReportsLoad() {
        raft.setLoadReporter(new RaftCoordinator.LoadReporter() {
            @Override
            public int inFlight() {
                return 3;
            }

            @Override
            public int capacity() {
                return 4;
            }
        });

        HeartbeatResponse response = raft.handleHeartbeat(HeartbeatRequest.newBuilder()
                .setTerm(1)
                .setLeaderId("node-b:19090")
                .build());

        assertThat(response.getStatus().getNodeId()).isEqualTo("node-a:19090");
        assertThat(response.getStatus().getInFlightTasks()).isEqualTo(3);
        assertThat(response.getStatus().getCapacity()).isEqualTo(4);
    }

    private static VoteRequest voteRequest(long term, String candidateId) {
        return VoteRequest.newBuilder()
                .setTerm(term)
                .setCandidateId(candidateId)
                .setLastTaskIndex(0)
                .build();
    }
}
