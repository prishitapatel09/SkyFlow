package com.skyflow.flight.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record FlightDto(
        Long id,
        String flightNumber,
        AirportDto departureAirport,
        AirportDto arrivalAirport,
        AirplaneDto airplane,
        Instant departureTime,
        Instant arrivalTime,
        long durationMinutes,
        BigDecimal price,
        String boardingGate,
        int totalSeats,
        int availableSeats,
        String status) {

    public static long minutesBetween(Instant departure, Instant arrival) {
        if (departure == null || arrival == null) {
            return 0;
        }
        return Duration.between(departure, arrival).toMinutes();
    }
}
