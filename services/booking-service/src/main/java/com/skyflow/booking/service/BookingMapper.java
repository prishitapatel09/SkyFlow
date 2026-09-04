package com.skyflow.booking.service;

import com.skyflow.booking.domain.Booking;
import com.skyflow.booking.domain.Passenger;
import com.skyflow.booking.dto.BookingDto;
import com.skyflow.booking.dto.PassengerDto;

public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingDto toDto(Booking booking) {
        return new BookingDto(
                booking.getId(),
                booking.getBookingReference(),
                booking.getUserId(),
                booking.getContactEmail(),
                booking.getContactName(),
                booking.getFlightId(),
                booking.getFlightNumber(),
                booking.getOriginCode(),
                booking.getDestinationCode(),
                booking.getDepartureTime(),
                booking.getArrivalTime(),
                booking.getSeats(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus(),
                booking.getPaymentIntentId(),
                booking.getHoldExpiresAt(),
                booking.getPassengers().stream().map(BookingMapper::toDto).toList(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    private static PassengerDto toDto(Passenger passenger) {
        return new PassengerDto(passenger.getId(), passenger.getFullName(),
                passenger.getSeatNumber(), passenger.getPassportNumber());
    }
}
