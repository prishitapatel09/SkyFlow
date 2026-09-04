package com.skyflow.flight.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AirplaneRequest(
        @NotBlank(message = "modelNumber is required") String modelNumber,
        @Min(value = 1, message = "capacity must be at least 1")
        @Max(value = 1000, message = "capacity must be at most 1000") int capacity) {
}
