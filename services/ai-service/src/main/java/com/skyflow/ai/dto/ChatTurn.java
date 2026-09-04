package com.skyflow.ai.dto;

/**
 * One stored conversation turn.
 *
 * <p>Only the text of each turn is persisted, not the tool-call blocks: tool results are large,
 * expire quickly (prices move), and replaying stale ones is worse than letting the assistant look
 * things up again.
 */
public record ChatTurn(String role, String text) {

    public static ChatTurn user(String text) {
        return new ChatTurn("user", text);
    }

    public static ChatTurn assistant(String text) {
        return new ChatTurn("assistant", text);
    }
}
