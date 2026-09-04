package com.skyflow.cluster;

/** Raft's three roles. Only a {@link #LEADER} distributes tasks. */
public enum NodeRole {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
