package com.skyflow.booking.dto;

/**
 * What the checkout page needs in a single round trip: the booking, plus the Stripe client secret
 * to confirm it with.
 */
public record BookingCreatedDto(BookingDto booking, PaymentIntentDto payment) {
}
