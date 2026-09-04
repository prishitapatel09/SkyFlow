package com.skyflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDetailsDto(
        String paymentIntentId,
        String bookingId,
        String flightId,
        String customerId,
        String customerEmail,
        BigDecimal amount,
        String currency,
        String status,
        BigDecimal refundedAmount,
        String failureMessage,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
