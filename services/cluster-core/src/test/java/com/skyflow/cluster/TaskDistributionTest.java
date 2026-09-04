package com.skyflow.cluster;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end master-worker behaviour on a single node: the leader queues work, dispatches it to
 * itself, and retries what fails. Multi-node dispatch goes over gRPC, which
 * {@link RaftCoordinatorTest} covers at the RPC-handler level.
 */
class TaskDistributionTest {

    private static final String TYPE = "test.task";

    private ClusterProperties properties;
    private PeerChannels peerChannels;
    private WorkerRegistry registry;
    private RaftCoordinator raft;
    private WorkerTaskExecutor worker;
    private MasterTaskDistributor master;

    @BeforeEach
    void setUp() {
        properties = new ClusterProperties();
        properties.setAdvertisedHost("node-a");
        properties.setGrpcPort(19091);
        properties.setElectionTimeoutMin(Duration.ofMillis(100));
        properties.setElectionTimeoutMax(Duration.ofMillis(160));
        properties.setHeartbeatInterval(Duration.ofMillis(50));
        properties.setWorkerCapacity(2);
        properties.setMaxTaskAttempts(3);

        peerChannels = new PeerChannels();
        registry = new WorkerRegistry(properties);
        raft = new RaftCoordinator(properties, peerChannels, registry);
    }

    private void startCluster(ClusterTaskHandler handler) {
        worker = new WorkerTaskExecutor(properties, raft, peerChannels, List.of(handler));
        raft.setLoadReporter(worker);
        master = new MasterTaskDistributor(properties, raft, registry, peerChannels, worker);
        master.start();
        raft.start();
        await().atMost(Duration.ofSeconds(2)).until(raft::isLeader);
    }

    @AfterEach
    void tearDown() {
        if (master != null) {
            master.close();
        }
        if (worker != null) {
            worker.close();
        }
        raft.close();
        peerChannels.close();
    }

    @Test
    @DisplayName("the leader dispatches queued work to a worker and clears it from the queue")
    void dispatchesQueuedWork() {
        List<String> handled = new CopyOnWriteArrayList<>();
        startCluster(handler(task -> handled.add(task.payloadJson())));

        assertThat(master.submitIfLeader(TYPE, "{\"n\":1}")).isTrue();
        assertThat(master.submitIfLeader(TYPE, "{\"n\":2}")).isTrue();

        await().atMost(Duration.ofSeconds(3)).until(() -> handled.size() == 2);
        assertThat(handled).containsExactlyInAnyOrder("{\"n\":1}", "{\"n\":2}");
        await().atMost(Duration.ofSeconds(2))
                .until(() -> master.pendingCount() == 0 && master.inFlightCount() == 0);
    }

    @Test
    @DisplayName("a failed task is reassigned until max-task-attempts is exhausted, then dropped")
    void retriesFailuresUpToTheAttemptLimit() {
        AtomicInteger attempts = new AtomicInteger();
        startCluster(handler(task -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        }));

        assertThat(master.submitIfLeader(TYPE, "{}")).isTrue();

        // Three attempts total: the first plus two reassignments.
        await().atMost(Duration.ofSeconds(5)).until(() -> attempts.get() == 3);
        await().atMost(Duration.ofSeconds(2))
                .until(() -> master.pendingCount() == 0 && master.inFlightCount() == 0);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("a task of an unregistered type is refused rather than queued forever")
    void refusesUnknownTaskTypes() {
        startCluster(handler(task -> { }));

        assertThat(master.submitIfLeader("no.such.task", "{}")).isFalse();
        assertThat(master.pendingCount()).isZero();
    }

    @Test
    @DisplayName("a worker at capacity refuses new assignments so the master can hold the work")
    void refusesWorkBeyondCapacity() throws Exception {
        properties.setWorkerCapacity(1);
        AtomicInteger started = new AtomicInteger();
        startCluster(handler(task -> {
            started.incrementAndGet();
            Thread.sleep(400);
        }));

        master.submitIfLeader(TYPE, "{\"n\":1}");
        master.submitIfLeader(TYPE, "{\"n\":2}");

        // Only one runs at a time; the second waits rather than being lost.
        await().atMost(Duration.ofSeconds(1)).until(() -> started.get() == 1);
        await().atMost(Duration.ofSeconds(4)).until(() -> started.get() == 2);
    }

    @Test
    @DisplayName("losing leadership releases queued work instead of running it twice")
    void releasesQueueOnLostLeadership() {
        startCluster(handler(task -> { }));

        // A leader with a full queue that is superseded must not keep dispatching: the new leader
        // owns the work now.
        master.submitIfLeader(TYPE, "{}");
        master.onLostLeadership(raft.currentTerm());

        assertThat(master.pendingCount()).isZero();
        assertThat(master.inFlightCount()).isZero();
    }

    private static ClusterTaskHandler handler(ThrowingConsumer body) {
        return new ClusterTaskHandler() {
            @Override
            public String type() {
                return TYPE;
            }

            @Override
            public void handle(ClusterTask task) throws Exception {
                body.accept(task);
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingConsumer {
        void accept(ClusterTask task) throws Exception;
    }
}
