package com.skyflow.cluster;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the coordination cluster formed by the replicas of a single service.
 *
 * <p>A node's identity <em>is</em> its advertised gRPC endpoint ({@code host:port}), which means a
 * leader id is directly dialable by every follower and no separate discovery lookup is needed.
 * Under Kubernetes the replicas run as a StatefulSet behind a headless service, so
 * {@code advertised-host} is set to {@code $(POD_NAME).<headless-service>} and {@code peers} to
 * the full, stable list of pod addresses.
 */
@ConfigurationProperties(prefix = "skyflow.cluster")
public class ClusterProperties {

    /** Turns the whole coordination layer off (useful for tests and single-process dev runs). */
    private boolean enabled = true;

    /** Host name this node advertises to its peers. Defaults to {@code HOSTNAME}. */
    private String advertisedHost;

    /** Port the coordination gRPC server binds to. */
    private int grpcPort = 9090;

    /** Every replica's {@code host:port}, including this one. Self is filtered out at startup. */
    private List<String> peers = new ArrayList<>();

    /** How often the leader heartbeats its followers. */
    private Duration heartbeatInterval = Duration.ofMillis(500);

    /** Lower bound of the randomized election timeout. */
    private Duration electionTimeoutMin = Duration.ofMillis(1500);

    /** Upper bound of the randomized election timeout. Randomization avoids split votes. */
    private Duration electionTimeoutMax = Duration.ofMillis(3000);

    /** Consecutive missed heartbeats before the leader declares a worker dead. */
    private int failureThreshold = 3;

    /** Per-RPC deadline. Kept well under the heartbeat interval so a dead peer cannot stall a tick. */
    private Duration rpcDeadline = Duration.ofMillis(400);

    /** Tasks this node will run concurrently. */
    private int workerCapacity = 4;

    /** How long a worker may hold a task before the master reassigns it. */
    private Duration taskTimeout = Duration.ofSeconds(60);

    /** Attempts (including the first) before a task is dropped and logged. */
    private int maxTaskAttempts = 3;

    /** Whether the leader also executes tasks instead of only distributing them. */
    private boolean leaderExecutesTasks = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdvertisedHost() {
        return advertisedHost;
    }

    public void setAdvertisedHost(String advertisedHost) {
        this.advertisedHost = advertisedHost;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public void setGrpcPort(int grpcPort) {
        this.grpcPort = grpcPort;
    }

    public List<String> getPeers() {
        return peers;
    }

    public void setPeers(List<String> peers) {
        this.peers = peers;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getElectionTimeoutMin() {
        return electionTimeoutMin;
    }

    public void setElectionTimeoutMin(Duration electionTimeoutMin) {
        this.electionTimeoutMin = electionTimeoutMin;
    }

    public Duration getElectionTimeoutMax() {
        return electionTimeoutMax;
    }

    public void setElectionTimeoutMax(Duration electionTimeoutMax) {
        this.electionTimeoutMax = electionTimeoutMax;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public Duration getRpcDeadline() {
        return rpcDeadline;
    }

    public void setRpcDeadline(Duration rpcDeadline) {
        this.rpcDeadline = rpcDeadline;
    }

    public int getWorkerCapacity() {
        return workerCapacity;
    }

    public void setWorkerCapacity(int workerCapacity) {
        this.workerCapacity = workerCapacity;
    }

    public Duration getTaskTimeout() {
        return taskTimeout;
    }

    public void setTaskTimeout(Duration taskTimeout) {
        this.taskTimeout = taskTimeout;
    }

    public int getMaxTaskAttempts() {
        return maxTaskAttempts;
    }

    public void setMaxTaskAttempts(int maxTaskAttempts) {
        this.maxTaskAttempts = maxTaskAttempts;
    }

    public boolean isLeaderExecutesTasks() {
        return leaderExecutesTasks;
    }

    public void setLeaderExecutesTasks(boolean leaderExecutesTasks) {
        this.leaderExecutesTasks = leaderExecutesTasks;
    }

    /** This node's identity: the endpoint its peers dial. */
    public String nodeId() {
        return resolvedHost() + ":" + grpcPort;
    }

    private String resolvedHost() {
        if (advertisedHost != null && !advertisedHost.isBlank()) {
            return advertisedHost;
        }
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "localhost" : hostname;
    }

    /** Configured peers with this node removed. */
    public List<String> otherPeers() {
        String self = nodeId();
        return peers.stream().map(String::trim).filter(p -> !p.isEmpty() && !p.equals(self)).toList();
    }

    /**
     * Votes needed to win an election: a strict majority of the whole cluster. With no peers
     * configured (single-process development) this is 1, so the lone node leads immediately.
     */
    public int quorum() {
        int clusterSize = Math.max(1, otherPeers().size() + 1);
        return clusterSize / 2 + 1;
    }
}
