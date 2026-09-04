package com.skyflow.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard response envelope for every SkyFlow HTTP endpoint.
 *
 * <p>The field names ({@code data}, {@code success}, {@code message}, {@code err}) are the same
 * envelope the original Express API returned, so existing clients keep working unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Object err,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "OK");
    }

    public static <T> ApiResponse<T> failure(String message, Object err) {
        return new ApiResponse<>(false, message, null, err, Instant.now());
    }
}
