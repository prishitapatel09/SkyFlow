package com.skyflow.flight.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** Replaces the hand-written {@code validateCreateFlight} middleware with bean validation. */
public record CreateFlightRequest(
        @NotBlank(message = "flightNumber is required") String flightNumber,
        @NotNull(message = "airplaneId is required") Long airplaneId,
        @NotNull(message = "departureAirportId is required") Long departureAirportId,
        @NotNull(message = "arrivalAirportId is required") Long arrivalAirportId,
        @NotNull(message = "departureTime is required") Instant departureTime,
        @NotNull(message = "arrivalTime is required") Instant arrivalTime,
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive") BigDecimal price,
        String boardingGate) {
}
