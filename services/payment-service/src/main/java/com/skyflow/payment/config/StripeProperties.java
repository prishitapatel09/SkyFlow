package com.skyflow.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "skyflow.stripe")
public class StripeProperties {

    private String secretKey = "";

    private String publishableKey = "";

    /** Signing secret for the webhook endpoint. Requests are rejected outright without it. */
    private String webhookSecret = "";

    private String currency = "usd";

    private List<String> paymentMethods = List.of("card");

    private List<String> supportedCountries = List.of("US", "IN", "CA", "GB", "AU");

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPublishableKey() {
        return publishableKey;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<String> getPaymentMethods() {
        return paymentMethods;
    }

    public void setPaymentMethods(List<String> paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    public List<String> getSupportedCountries() {
        return supportedCountries;
    }

    public void setSupportedCountries(List<String> supportedCountries) {
        this.supportedCountries = supportedCountries;
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
