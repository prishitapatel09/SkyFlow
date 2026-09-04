package com.skyflow.payment.dto;

import java.util.List;

/** What the checkout page needs to render: publishable key, methods and supported countries. */
public record PaymentConfigDto(String publishableKey, String currency, List<String> paymentMethods,
                               List<String> supportedCountries) {
}
