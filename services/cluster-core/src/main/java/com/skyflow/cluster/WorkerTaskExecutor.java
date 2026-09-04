package com.skyflow.cluster;

import com.skyflow.cluster.grpc.TaskAck;
import com.skyflow.cluster.grpc.TaskAssignment;
import com.skyflow.cluster.grpc.TaskResult;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Worker side of the master-worker split: runs the tasks the leader assigns and reports the
 * outcome back. Every replica has one of these, including the leader.
 */
public class WorkerTaskExecutor implements RaftCoordinator.LoadReporter, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkerTaskExecutor.class);

    private final ClusterProperties properties;
    private final RaftCoordinator raft;
    private final PeerChannels peerChannels;
    private final Map<String, ClusterTaskHandler> handlers;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final ExecutorService pool;

    /** Set once the distributor exists, so a leader executing its own task skips the network. */
    private volatile Consumer<TaskResult> localResultSink = result -> { };

    private volatile boolean draining;

    public WorkerTaskExecutor(ClusterProperties properties, RaftCoordinator raft,
                              PeerChannels peerChannels, List<ClusterTaskHandler> handlers) {
        this.properties = properties;
        this.raft = raft;
        this.peerChannels = peerChannels;
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(ClusterTaskHandler::type, Function.identity()));
        this.pool = Executors.newFixedThreadPool(Math.max(1, properties.getWorkerCapacity()), runnable -> {
            Thread thread = new Thread(runnable, "cluster-worker");
            thread.setDaemon(true);
            return thread;
        });
        log.info("Worker registered {} task handler(s): {}", this.handlers.size(), this.handlers.keySet());
    }

    public void setLocalResultSink(Consumer<TaskResult> localResultSink) {
        this.localResultSink = localResultSink;
    }

    /** Accepts or refuses an assignment. Refusals are not failures - the master just re-dispatches. */
    public TaskAck accept(TaskAssignment assignment) {
        if (draining) {
            return refuse("draining");
        }
        if (!handlers.containsKey(assignment.getType())) {
            return refuse("no handler for type " + assignment.getType());
        }
        if (inFlight.get() >= properties.getWorkerCapacity()) {
            return refuse("at capacity");
        }

        ClusterTask task = new ClusterTask(
                assignment.getTaskId(),
                assignment.getType(),
                assignment.getPayloadJson(),
                Math.max(1, assignment.getAttempt()),
                Instant.now());

        inFlight.incrementAndGet();
        raft.observeTask();
        try {
            pool.execute(() -> run(task));
        } catch (RuntimeException ex) {
            inFlight.decrementAndGet();
            return refuse("executor rejected task");
        }
        return TaskAck.newBuilder().setAccepted(true).setNodeId(raft.nodeId()).build();
    }

    private void run(ClusterTask task) {
        long startedAt = System.nanoTime();
        boolean success = true;
        String message = "ok";
        try {
            handlers.get(task.type()).handle(task);
        } catch (Exception ex) {
            success = false;
            message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            log.error("Task {} ({}) attempt {} failed", task.id(), task.type(), task.attempt(), ex);
        } finally {
            inFlight.decrementAndGet();
        }

        report(TaskResult.newBuilder()
                .setTaskId(task.id())
                .setWorkerId(raft.nodeId())
                .setSuccess(success)
                .setMessage(message)
                .setDurationMillis(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                .build());
    }

    private void report(TaskResult result) {
        String leader = raft.leaderId();
        if (leader == null) {
            log.warn("Task {} finished but no leader is known; result dropped", result.getTaskId());
            return;
        }
        if (leader.equals(raft.nodeId())) {
            localResultSink.accept(result);
            return;
        }
        try {
            peerChannels.stub(leader)
                    .withDeadlineAfter(properties.getRpcDeadline().toMillis(), TimeUnit.MILLISECONDS)
                    .reportTaskResult(result);
        } catch (StatusRuntimeException ex) {
            // The master will time the task out and reassign it; nothing else to do here.
            log.warn("Could not report task {} to leader {}: {}",
                    result.getTaskId(), leader, ex.getStatus().getCode());
            peerChannels.reset(leader);
        }
    }

    private TaskAck refuse(String reason) {
        return TaskAck.newBuilder().setAccepted(false).setNodeId(raft.nodeId()).setReason(reason).build();
    }

    @Override
    public int inFlight() {
        return inFlight.get();
    }

    @Override
    public int capacity() {
        return properties.getWorkerCapacity();
    }

    public boolean hasHandler(String type) {
        return handlers.containsKey(type);
    }

    @Override
    public void close() {
        draining = true;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
