package com.skyflow.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AirportRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "code is required")
        @Size(min = 3, max = 3, message = "code must be a 3 letter IATA code") String code,
        String address,
        @NotNull(message = "cityId is required") Long cityId) {
}
