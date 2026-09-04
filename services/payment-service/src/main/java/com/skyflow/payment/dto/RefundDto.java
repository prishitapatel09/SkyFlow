package com.skyflow.payment.dto;

import java.math.BigDecimal;

public record RefundDto(String refundId, String paymentIntentId, BigDecimal amount, String status,
                        String reason) {
}
