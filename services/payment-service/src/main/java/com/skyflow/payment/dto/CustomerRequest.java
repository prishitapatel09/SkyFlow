package com.skyflow.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        String name,
        String phone,
        String userId) {
}
