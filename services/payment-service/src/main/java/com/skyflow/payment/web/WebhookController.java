package com.skyflow.payment.web;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.payment.service.StripeWebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Stripe webhook receiver. The body is taken as a raw {@code String} because the signature is
 * computed over the exact bytes Stripe sent - re-serializing a parsed object would break it.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final StripeWebhookService webhookService;

    public WebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/stripe", consumes = MediaType.ALL_VALUE)
    public ApiResponse<Map<String, String>> stripe(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        String eventType = webhookService.handle(payload, signature);
        return ApiResponse.success(Map.of("received", "true", "type", eventType), "Webhook handled");
    }
}
