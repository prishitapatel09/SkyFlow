package com.skyflow.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record PassengerRequest(
        @NotBlank(message = "passenger fullName is required") String fullName,
        String passportNumber) {
}
