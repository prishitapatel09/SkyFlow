package com.skyflow.cluster;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Wires the coordination cluster. A service opts in by putting {@code skyflow-cluster-core} on its
 * classpath and declaring at least one {@link ClusterTaskHandler} bean; set
 * {@code skyflow.cluster.enabled=false} to run a single process with no coordination at all.
 */
@AutoConfiguration
@EnableConfigurationProperties(ClusterProperties.class)
@ConditionalOnProperty(prefix = "skyflow.cluster", name = "enabled", matchIfMissing = true)
public class ClusterAutoConfiguration {

    @Bean(destroyMethod = "close")
    public PeerChannels peerChannels() {
        return new PeerChannels();
    }

    @Bean
    public WorkerRegistry workerRegistry(ClusterProperties properties) {
        return new WorkerRegistry(properties);
    }

    @Bean(destroyMethod = "close")
    public RaftCoordinator raftCoordinator(ClusterProperties properties, PeerChannels peerChannels,
                                           WorkerRegistry workerRegistry) {
        return new RaftCoordinator(properties, peerChannels, workerRegistry);
    }

    @Bean(destroyMethod = "close")
    public WorkerTaskExecutor workerTaskExecutor(ClusterProperties properties, RaftCoordinator raft,
                                                 PeerChannels peerChannels,
                                                 List<ClusterTaskHandler> handlers) {
        WorkerTaskExecutor executor = new WorkerTaskExecutor(properties, raft, peerChannels, handlers);
        raft.setLoadReporter(executor);
        return executor;
    }

    @Bean(destroyMethod = "close")
    public MasterTaskDistributor masterTaskDistributor(ClusterProperties properties, RaftCoordinator raft,
                                                       WorkerRegistry workerRegistry,
                                                       PeerChannels peerChannels,
                                                       WorkerTaskExecutor workerTaskExecutor) {
        return new MasterTaskDistributor(properties, raft, workerRegistry, peerChannels, workerTaskExecutor);
    }

    @Bean
    public ClusterCoordinationService clusterCoordinationService(RaftCoordinator raft,
                                                                 WorkerTaskExecutor worker,
                                                                 MasterTaskDistributor master) {
        return new ClusterCoordinationService(raft, worker, master);
    }

    @Bean
    public ClusterServerLifecycle clusterServerLifecycle(ClusterProperties properties,
                                                         ClusterCoordinationService service,
                                                         RaftCoordinator raft,
                                                         MasterTaskDistributor master) {
        return new ClusterServerLifecycle(properties, service, raft, master);
    }

    @Bean
    public ClusterStatusProvider clusterStatusProvider(ClusterProperties properties, RaftCoordinator raft,
                                                       WorkerRegistry workerRegistry,
                                                       WorkerTaskExecutor worker,
                                                       MasterTaskDistributor master) {
        return new ClusterStatusProvider(properties, raft, workerRegistry, worker, master);
    }
}
