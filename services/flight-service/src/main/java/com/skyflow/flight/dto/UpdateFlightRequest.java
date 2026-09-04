package com.skyflow.flight.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;

/** Every field is optional; nulls leave the current value in place. */
public record UpdateFlightRequest(
        Instant departureTime,
        Instant arrivalTime,
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive") BigDecimal price,
        String boardingGate,
        String status) {
}
