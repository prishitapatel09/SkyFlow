package com.skyflow.payment.dto;

import java.math.BigDecimal;

/**
 * The client secret is included because the browser needs it to confirm the payment; nothing else
 * about the intent is exposed.
 */
public record PaymentIntentDto(String paymentIntentId, String clientSecret, BigDecimal amount,
                               String currency, String status) {
}
