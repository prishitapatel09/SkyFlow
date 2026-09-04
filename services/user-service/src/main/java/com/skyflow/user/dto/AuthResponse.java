package com.skyflow.user.dto;

/** Shape the React auth store already reads: {@code data.token}, {@code data.user}. */
public record AuthResponse(String token, String refreshToken, long expiresIn, UserDto user) {
}
