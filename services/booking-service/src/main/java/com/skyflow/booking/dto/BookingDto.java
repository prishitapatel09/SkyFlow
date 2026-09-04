package com.skyflow.booking.dto;

import com.skyflow.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingDto(
        Long id,
        String bookingReference,
        String userId,
        String contactEmail,
        String contactName,
        Long flightId,
        String flightNumber,
        String originCode,
        String destinationCode,
        Instant departureTime,
        Instant arrivalTime,
        int seats,
        BigDecimal totalAmount,
        String currency,
        BookingStatus status,
        String paymentIntentId,
        Instant holdExpiresAt,
        List<PassengerDto> passengers,
        Instant createdAt,
        Instant updatedAt) {
}
