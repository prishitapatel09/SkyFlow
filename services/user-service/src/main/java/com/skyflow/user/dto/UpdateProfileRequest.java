package com.skyflow.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateProfileRequest(String name, @Email(message = "email must be valid") String email,
                                   String phone) {
}
