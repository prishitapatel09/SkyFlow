package com.skyflow.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CityRequest(
        @NotBlank(message = "name is required") String name,
        @Size(min = 2, max = 2, message = "countryCode must be a 2 letter code") String countryCode) {
}
