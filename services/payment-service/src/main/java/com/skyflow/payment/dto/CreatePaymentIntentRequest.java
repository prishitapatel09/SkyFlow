package com.skyflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record CreatePaymentIntentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.5", message = "amount must be at least 0.50") BigDecimal amount,
        String currency,
        String customerId,
        @Email(message = "customerEmail must be valid") String customerEmail,
        String bookingId,
        String flightId,
        String description,
        Map<String, String> metadata) {
}
