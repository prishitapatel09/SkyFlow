package com.skyflow.cluster;

import com.skyflow.cluster.grpc.ClusterCoordinationGrpc;
import com.skyflow.cluster.grpc.HeartbeatRequest;
import com.skyflow.cluster.grpc.HeartbeatResponse;
import com.skyflow.cluster.grpc.TaskAck;
import com.skyflow.cluster.grpc.TaskAssignment;
import com.skyflow.cluster.grpc.TaskResult;
import com.skyflow.cluster.grpc.VoteRequest;
import com.skyflow.cluster.grpc.VoteResponse;
import io.grpc.stub.StreamObserver;

/** Server side of the coordination plane; every replica exposes it. */
public class ClusterCoordinationService extends ClusterCoordinationGrpc.ClusterCoordinationImplBase {

    private final RaftCoordinator raft;
    private final WorkerTaskExecutor worker;
    private final MasterTaskDistributor master;

    public ClusterCoordinationService(RaftCoordinator raft, WorkerTaskExecutor worker,
                                     MasterTaskDistributor master) {
        this.raft = raft;
        this.worker = worker;
        this.master = master;
    }

    @Override
    public void requestVote(VoteRequest request, StreamObserver<VoteResponse> responseObserver) {
        responseObserver.onNext(raft.handleVoteRequest(request));
        responseObserver.onCompleted();
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        responseObserver.onNext(raft.handleHeartbeat(request));
        responseObserver.onCompleted();
    }

    @Override
    public void assignTask(TaskAssignment request, StreamObserver<TaskAck> responseObserver) {
        // A task from a stale leader is refused so a partitioned old master cannot keep dispatching.
        if (request.getTerm() < raft.currentTerm()) {
            responseObserver.onNext(TaskAck.newBuilder()
                    .setAccepted(false)
                    .setNodeId(raft.nodeId())
                    .setReason("stale term " + request.getTerm())
                    .build());
            responseObserver.onCompleted();
            return;
        }
        responseObserver.onNext(worker.accept(request));
        responseObserver.onCompleted();
    }

    @Override
    public void reportTaskResult(TaskResult request, StreamObserver<TaskAck> responseObserver) {
        master.onTaskResult(request);
        responseObserver.onNext(TaskAck.newBuilder().setAccepted(true).setNodeId(raft.nodeId()).build());
        responseObserver.onCompleted();
    }
}
