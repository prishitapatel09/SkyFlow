package com.skyflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** A null {@code amount} refunds the payment in full. */
public record RefundRequest(
        @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
        String reason) {
}
