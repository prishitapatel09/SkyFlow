package com.skyflow.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One passenger entry per seat: the seat count is derived from the list rather than sent
 * separately, so the two can never disagree.
 */
public record CreateBookingRequest(
        @NotNull(message = "flightId is required") Long flightId,
        @NotEmpty(message = "at least one passenger is required")
        @Size(max = 9, message = "at most 9 passengers per booking")
        @Valid List<PassengerRequest> passengers,
        @Email(message = "contactEmail must be valid") String contactEmail,
        String contactName) {

    public int seats() {
        return passengers == null ? 0 : passengers.size();
    }
}
