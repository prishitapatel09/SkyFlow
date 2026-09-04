package com.skyflow.flight.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SeatChangeRequest(
        @Min(value = 1, message = "seats must be at least 1")
        @Max(value = 9, message = "seats must be at most 9") int seats) {
}
