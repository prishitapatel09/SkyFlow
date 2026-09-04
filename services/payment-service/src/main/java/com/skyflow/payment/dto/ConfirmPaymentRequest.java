package com.skyflow.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPaymentRequest(
        @NotBlank(message = "paymentMethodId is required") String paymentMethodId) {
}
