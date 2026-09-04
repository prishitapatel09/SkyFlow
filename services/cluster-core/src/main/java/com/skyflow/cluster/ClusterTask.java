package com.skyflow.cluster;

import java.time.Instant;
import java.util.UUID;

/**
 * A unit of work the master hands to a worker.
 *
 * @param id          unique across retries, so a late result from a timed-out attempt is ignored
 * @param type        key a {@link ClusterTaskHandler} registers under
 * @param payloadJson opaque to the cluster layer; the handler owns its schema
 * @param attempt     1 for the first dispatch, incremented on every reassignment
 */
public record ClusterTask(String id, String type, String payloadJson, int attempt, Instant enqueuedAt) {

    public static ClusterTask of(String type, String payloadJson) {
        return new ClusterTask(UUID.randomUUID().toString(), type, payloadJson == null ? "{}" : payloadJson,
                1, Instant.now());
    }

    public ClusterTask nextAttempt() {
        return new ClusterTask(id, type, payloadJson, attempt + 1, enqueuedAt);
    }
}
