package com.skyflow.user.dto;

import java.time.Instant;

public record UserDto(Long id, String name, String email, String role, String phone,
                      Instant createdAt, Instant updatedAt) {
}
