package com.skyflow.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The leader's view of its workers, maintained purely from heartbeat outcomes: a successful
 * {@code Heartbeat} carries the worker's live load, and {@code failureThreshold} consecutive
 * failures declare the worker dead so its in-flight work can be reassigned.
 */
public class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    private final ClusterProperties properties;
    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final List<Consumer<String>> deathListeners = new ArrayList<>();

    public WorkerRegistry(ClusterProperties properties) {
        this.properties = properties;
    }

    /** Registers a callback invoked with the node id of a worker that has just been declared dead. */
    public void onWorkerDeath(Consumer<String> listener) {
        deathListeners.add(listener);
    }

    public void track(String nodeId) {
        workers.computeIfAbsent(nodeId, Worker::new);
    }

    /** A heartbeat round-tripped: the worker is alive and reported its current load. */
    public void recordAlive(String nodeId, int inFlight, int capacity) {
        Worker worker = workers.computeIfAbsent(nodeId, Worker::new);
        boolean wasDead = !worker.alive;
        worker.alive = true;
        worker.missed.set(0);
        worker.inFlight = inFlight;
        worker.capacity = capacity;
        worker.lastSeen = System.currentTimeMillis();
        if (wasDead) {
            log.info("Worker {} is back; adding it to the dispatch pool", nodeId);
        }
    }

    /** A heartbeat failed. Declares the worker dead once the failure threshold is crossed. */
    public void recordMiss(String nodeId) {
        Worker worker = workers.computeIfAbsent(nodeId, Worker::new);
        int missed = worker.missed.incrementAndGet();
        if (worker.alive && missed >= properties.getFailureThreshold()) {
            worker.alive = false;
            worker.inFlight = 0;
            log.warn("Worker {} missed {} heartbeats; marking it dead and reassigning its tasks",
                    nodeId, missed);
            deathListeners.forEach(listener -> listener.accept(nodeId));
        }
    }

    /** Optimistic local accounting so a burst of assignments does not all land on one worker. */
    public void incrementInFlight(String nodeId) {
        Worker worker = workers.get(nodeId);
        if (worker != null) {
            worker.inFlight++;
        }
    }

    public void decrementInFlight(String nodeId) {
        Worker worker = workers.get(nodeId);
        if (worker != null && worker.inFlight > 0) {
            worker.inFlight--;
        }
    }

    /** Clears load accounting when this node takes over as leader. */
    public void resetForNewTerm() {
        workers.values().forEach(worker -> {
            worker.inFlight = 0;
            worker.missed.set(0);
            worker.alive = true;
        });
    }

    public List<ClusterStatus.WorkerSnapshot> snapshot() {
        return workers.values().stream()
                .map(worker -> new ClusterStatus.WorkerSnapshot(
                        worker.nodeId, worker.alive, worker.inFlight, worker.capacity,
                        worker.missed.get(), worker.lastSeen == 0 ? null : worker.lastSeen))
                .sorted((a, b) -> a.nodeId().compareTo(b.nodeId()))
                .toList();
    }

    private static final class Worker {
        private final String nodeId;
        private final AtomicInteger missed = new AtomicInteger();
        private volatile boolean alive = true;
        private volatile int inFlight;
        private volatile int capacity = 1;
        private volatile long lastSeen;

        private Worker(String nodeId) {
            this.nodeId = nodeId;
        }
    }
}
