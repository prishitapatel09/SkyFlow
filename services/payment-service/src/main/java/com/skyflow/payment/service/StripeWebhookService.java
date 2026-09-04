package com.skyflow.payment.service;

import com.skyflow.common.exception.BadRequestException;
import com.skyflow.payment.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Handles Stripe webhooks. Signature verification is mandatory: without it this endpoint is an
 * unauthenticated way to mark any booking paid, so a missing signing secret rejects the request
 * rather than skipping the check.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final StripeProperties properties;
    private final PaymentService paymentService;
    private final PaymentAuditService auditService;

    public StripeWebhookService(StripeProperties properties, PaymentService paymentService,
                                PaymentAuditService auditService) {
        this.properties = properties;
        this.paymentService = paymentService;
        this.auditService = auditService;
    }

    public String handle(String payload, String signatureHeader) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            throw new BadRequestException("Webhook signing secret is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BadRequestException("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, properties.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            log.warn("Rejected webhook with an invalid signature");
            throw new BadRequestException("Invalid webhook signature");
        }

        auditService.record("webhook." + event.getType(), null, Map.of("eventId", event.getId()));

        switch (event.getType()) {
            case "payment_intent.succeeded",
                 "payment_intent.payment_failed",
                 "payment_intent.canceled",
                 "payment_intent.processing" -> paymentIntentOf(event)
                    .ifPresent(paymentService::applyIntentState);
            case "charge.refunded" -> log.info("Charge refunded webhook received: {}", event.getId());
            default -> log.debug("Ignoring unhandled Stripe event type {}", event.getType());
        }
        return event.getType();
    }

    private Optional<PaymentIntent> paymentIntentOf(Event event) {
        Optional<StripeObject> object = event.getDataObjectDeserializer().getObject();
        if (object.isEmpty()) {
            // Happens when Stripe's API version differs from the SDK's; the event id is enough to
            // investigate, and the polling endpoint can still reconcile the payment.
            log.warn("Could not deserialize the object on event {} ({})", event.getId(),
                    event.getType());
            return Optional.empty();
        }
        if (object.get() instanceof PaymentIntent intent) {
            return Optional.of(intent);
        }
        return Optional.empty();
    }
}
