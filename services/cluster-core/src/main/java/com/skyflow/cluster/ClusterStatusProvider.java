package com.skyflow.cluster;

/** Assembles a {@link ClusterStatus} for the {@code /api/v1/cluster/status} endpoint. */
public class ClusterStatusProvider {

    private final ClusterProperties properties;
    private final RaftCoordinator raft;
    private final WorkerRegistry workerRegistry;
    private final WorkerTaskExecutor worker;
    private final MasterTaskDistributor master;

    public ClusterStatusProvider(ClusterProperties properties, RaftCoordinator raft,
                                 WorkerRegistry workerRegistry, WorkerTaskExecutor worker,
                                 MasterTaskDistributor master) {
        this.properties = properties;
        this.raft = raft;
        this.workerRegistry = workerRegistry;
        this.worker = worker;
        this.master = master;
    }

    public ClusterStatus status() {
        return new ClusterStatus(
                raft.nodeId(),
                raft.role(),
                raft.currentTerm(),
                raft.leaderId(),
                properties.quorum(),
                master.pendingCount(),
                master.inFlightCount(),
                worker.inFlight(),
                raft.lastTaskIndex(),
                workerRegistry.snapshot());
    }
}
