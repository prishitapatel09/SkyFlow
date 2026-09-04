package com.skyflow.cluster;

/**
 * Worker-side handler for one task type. Every replica registers the same set of handlers, so any
 * worker can pick up any task and a reassignment after a node failure needs no special casing.
 */
public interface ClusterTaskHandler {

    /** Task type this handler claims; must match {@link ClusterTask#type()}. */
    String type();

    /**
     * Runs the task. Throwing marks the attempt failed, which sends it back to the master for
     * reassignment until {@code skyflow.cluster.max-task-attempts} is exhausted.
     */
    void handle(ClusterTask task) throws Exception;
}
