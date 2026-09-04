package com.skyflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "message is required")
        @Size(max = 2000, message = "message must be at most 2000 characters") String message,
        /** Omit to start a new conversation. */
        String conversationId) {
}
