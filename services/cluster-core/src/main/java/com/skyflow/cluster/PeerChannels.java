package com.skyflow.cluster;

import com.skyflow.cluster.grpc.ClusterCoordinationGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Lazily created, reused gRPC channels to peer replicas. Channels are plaintext because the
 * coordination plane never leaves the cluster network (a NetworkPolicy restricts the port to pods
 * of the same StatefulSet).
 */
public class PeerChannels implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PeerChannels.class);

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    /** @param target peer identity, i.e. {@code host:port}. */
    public ClusterCoordinationGrpc.ClusterCoordinationBlockingStub stub(String target) {
        return ClusterCoordinationGrpc.newBlockingStub(channel(target));
    }

    private ManagedChannel channel(String target) {
        return channels.computeIfAbsent(target, t -> ManagedChannelBuilder.forTarget(t)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build());
    }

    /** Drops a channel so the next call re-resolves DNS - a rescheduled pod gets a new IP. */
    public void reset(String target) {
        ManagedChannel channel = channels.remove(target);
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    @Override
    public void close() {
        channels.values().forEach(channel -> {
            try {
                channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ex) {
                log.debug("Error closing peer channel", ex);
            }
        });
        channels.clear();
    }
}
