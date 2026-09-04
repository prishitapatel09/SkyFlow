package com.skyflow.ai.dto;

import java.util.List;

/**
 * @param toolsUsed names of the tools the assistant called, surfaced so the UI can show "checked
 *                  live availability" rather than leaving the answer unexplained
 */
public record ChatResponse(String conversationId, String reply, List<String> toolsUsed) {
}
