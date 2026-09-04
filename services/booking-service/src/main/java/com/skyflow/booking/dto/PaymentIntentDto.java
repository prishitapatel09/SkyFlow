package com.skyflow.booking.dto;

import java.math.BigDecimal;

/** Subset of payment-service's response that the browser is allowed to see. */
public record PaymentIntentDto(String paymentIntentId, String clientSecret, BigDecimal amount,
                               String currency, String status) {
}
