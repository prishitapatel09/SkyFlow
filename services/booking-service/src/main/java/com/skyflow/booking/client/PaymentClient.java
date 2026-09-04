package com.skyflow.booking.client;

import com.skyflow.booking.dto.PaymentIntentDto;
import com.skyflow.common.api.ApiResponse;
import com.skyflow.common.config.ServiceEndpoints;
import com.skyflow.common.exception.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Creates the payment intent for a new booking and asks for refunds when one is cancelled. */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestClient restClient;

    public PaymentClient(RestClient.Builder builder, ServiceEndpoints endpoints) {
        this.restClient = builder.baseUrl(endpoints.getPayment()).build();
    }

    public PaymentIntentDto createIntent(String bookingReference, Long bookingId, Long flightId,
                                         BigDecimal amount, String currency, String customerEmail) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("bookingId", String.valueOf(bookingId));
        body.put("flightId", String.valueOf(flightId));
        body.put("customerEmail", customerEmail);
        body.put("description", "SkyFlow booking " + bookingReference);

        try {
            ApiResponse<PaymentIntentDto> response = restClient.post()
                    .uri("/api/v1/payments/intent")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<PaymentIntentDto>>() { });

            if (response == null || response.data() == null) {
                throw new UpstreamException("payment-service returned no payment intent");
            }
            return response.data();
        } catch (RestClientException ex) {
            throw new UpstreamException("payment-service is unavailable", ex);
        }
    }

    /**
     * Best-effort refund. A cancellation still succeeds if this fails - the booking is cancelled
     * and the failed refund is logged for the finance queue rather than blocking the traveller.
     */
    public void refund(String paymentIntentId, String reason) {
        try {
            restClient.post()
                    .uri("/api/v1/payments/{id}/refund", paymentIntentId)
                    .body(Map.of("reason", reason == null ? "requested_by_customer" : reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.error("Refund request for payment intent {} failed", paymentIntentId, ex);
        }
    }
}
