package com.skyflow.flight.dto;

public record SeatAvailabilityDto(Long flightId, int totalSeats, int availableSeats) {
}
