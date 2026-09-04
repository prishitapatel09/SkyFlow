package com.skyflow.cluster;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Starts the coordination gRPC server, then the election timer. Ordered to come up after the HTTP
 * connector so a pod is only ever asked for votes once it can actually serve traffic.
 */
public class ClusterServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ClusterServerLifecycle.class);

    private final ClusterProperties properties;
    private final ClusterCoordinationService coordinationService;
    private final RaftCoordinator raft;
    private final MasterTaskDistributor master;
    private final HealthStatusManager healthStatusManager = new HealthStatusManager();

    private Server server;
    private volatile boolean running;

    public ClusterServerLifecycle(ClusterProperties properties,
                                  ClusterCoordinationService coordinationService,
                                  RaftCoordinator raft,
                                  MasterTaskDistributor master) {
        this.properties = properties;
        this.coordinationService = coordinationService;
        this.raft = raft;
        this.master = master;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(properties.getGrpcPort())
                    .addService(coordinationService)
                    .addService(healthStatusManager.getHealthService())
                    .build()
                    .start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not start cluster gRPC server on port "
                    + properties.getGrpcPort(), ex);
        }
        log.info("Cluster coordination gRPC server listening on {}", properties.getGrpcPort());

        master.start();
        raft.start();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        healthStatusManager.enterTerminalState();
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
        log.info("Cluster coordination server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** After the web server, before shutdown of application beans. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1024;
    }
}
