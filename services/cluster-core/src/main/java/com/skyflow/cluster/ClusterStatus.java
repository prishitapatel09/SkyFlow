package com.skyflow.cluster;

import java.util.List;

/** Snapshot of coordination state, exposed over HTTP for dashboards and debugging. */
public record ClusterStatus(
        String nodeId,
        NodeRole role,
        long term,
        String leaderId,
        int quorum,
        int pendingTasks,
        int inFlightTasks,
        int localInFlightTasks,
        long lastTaskIndex,
        List<WorkerSnapshot> workers) {

    public record WorkerSnapshot(
            String nodeId,
            boolean alive,
            int inFlightTasks,
            int capacity,
            int missedHeartbeats,
            Long lastSeenEpochMillis) {
    }
}
