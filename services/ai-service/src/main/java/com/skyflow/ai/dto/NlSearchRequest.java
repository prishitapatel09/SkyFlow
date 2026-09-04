package com.skyflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "cheap morning flight from Boston to San Francisco next Friday, two of us". */
public record NlSearchRequest(
        @NotBlank(message = "query is required")
        @Size(max = 500, message = "query must be at most 500 characters") String query) {
}
