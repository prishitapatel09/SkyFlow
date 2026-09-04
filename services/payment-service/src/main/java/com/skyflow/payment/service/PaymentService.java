package com.skyflow.payment.service;

import com.skyflow.common.event.EventPublisher;
import com.skyflow.common.event.EventType;
import com.skyflow.common.exception.BadRequestException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.common.exception.UpstreamException;
import com.skyflow.payment.config.StripeProperties;
import com.skyflow.payment.domain.PaymentRecord;
import com.skyflow.payment.dto.ConfirmPaymentRequest;
import com.skyflow.payment.dto.CreatePaymentIntentRequest;
import com.skyflow.payment.dto.CustomerDto;
import com.skyflow.payment.dto.CustomerRequest;
import com.skyflow.payment.dto.PaymentConfigDto;
import com.skyflow.payment.dto.PaymentDetailsDto;
import com.skyflow.payment.dto.PaymentIntentDto;
import com.skyflow.payment.dto.RefundDto;
import com.skyflow.payment.dto.RefundRequest;
import com.skyflow.payment.repository.PaymentRecordRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentListParams;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stripe integration. Ported from {@code payment-service.js} with two changes worth calling out:
 * money is handled as {@link BigDecimal} and converted to minor units in one place, and every
 * state change is mirrored into a local {@code payments} row so booking-service can reconcile
 * without querying Stripe.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final BigDecimal MINOR_UNITS = BigDecimal.valueOf(100);

    private final StripeClient stripe;
    private final StripeProperties properties;
    private final PaymentRecordRepository payments;
    private final PaymentAuditService auditService;
    private final EventPublisher eventPublisher;

    public PaymentService(StripeClient stripe, StripeProperties properties,
                          PaymentRecordRepository payments, PaymentAuditService auditService,
                          EventPublisher eventPublisher) {
        this.stripe = stripe;
        this.properties = properties;
        this.payments = payments;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentIntentDto createIntent(CreatePaymentIntentRequest request) {
        String currency = (request.currency() == null || request.currency().isBlank()
                ? properties.getCurrency() : request.currency()).toLowerCase();

        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(toMinorUnits(request.amount()))
                .setCurrency(currency)
                .setDescription(request.description() == null
                        ? "SkyFlow flight booking" : request.description())
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods
                        .builder()
                        .setEnabled(true)
                        .build());

        if (request.customerId() != null && !request.customerId().isBlank()) {
            params.setCustomer(request.customerId());
        }
        if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
            params.setReceiptEmail(request.customerEmail());
        }
        // Metadata is how a Stripe webhook finds its way back to a booking.
        if (request.bookingId() != null) {
            params.putMetadata("bookingId", request.bookingId());
        }
        if (request.flightId() != null) {
            params.putMetadata("flightId", request.flightId());
        }
        if (request.metadata() != null) {
            request.metadata().forEach(params::putMetadata);
        }

        PaymentIntent intent = call(() -> stripe.paymentIntents().create(params.build()),
                "create payment intent");

        PaymentRecord record = new PaymentRecord();
        record.setPaymentIntentId(intent.getId());
        record.setBookingId(request.bookingId());
        record.setFlightId(request.flightId());
        record.setCustomerId(intent.getCustomer());
        record.setCustomerEmail(request.customerEmail());
        record.setAmount(request.amount());
        record.setCurrency(currency);
        record.setStatus(intent.getStatus());
        record.setDescription(request.description());
        payments.save(record);

        auditService.record("payment_intent.created", intent.getId(),
                Map.of("amount", request.amount(), "currency", currency,
                        "bookingId", String.valueOf(request.bookingId())));

        log.info("Created payment intent {} for {} {}", intent.getId(), request.amount(), currency);
        return toDto(intent);
    }

    /**
     * Server-side confirmation, kept for parity with the Express API. Browser checkout normally
     * confirms with the client secret instead, and the outcome arrives here by webhook.
     */
    @Transactional
    public PaymentIntentDto confirm(String paymentIntentId, ConfirmPaymentRequest request) {
        PaymentIntent intent = call(() -> stripe.paymentIntents().confirm(paymentIntentId,
                        PaymentIntentConfirmParams.builder()
                                .setPaymentMethod(request.paymentMethodId())
                                .build()),
                "confirm payment");

        applyIntentState(intent);
        auditService.record("payment_intent.confirmed", paymentIntentId,
                Map.of("status", intent.getStatus()));
        return toDto(intent);
    }

    @Transactional(readOnly = true)
    public PaymentDetailsDto get(String paymentIntentId) {
        return toDetails(payments.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentIntentId)));
    }

    /** Re-reads the intent from Stripe and stores whatever it reports, then emits the event. */
    @Transactional
    public PaymentDetailsDto syncFromStripe(String paymentIntentId) {
        PaymentIntent intent = call(() -> stripe.paymentIntents().retrieve(paymentIntentId),
                "retrieve payment intent");
        return toDetails(applyIntentState(intent));
    }

    @Transactional
    public RefundDto refund(String paymentIntentId, RefundRequest request) {
        PaymentRecord record = payments.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentIntentId));

        BigDecimal alreadyRefunded = record.getRefundedAmount() == null
                ? BigDecimal.ZERO : record.getRefundedAmount();
        BigDecimal refundable = record.getAmount().subtract(alreadyRefunded);
        BigDecimal amount = request.amount() == null ? refundable : request.amount();

        if (amount.compareTo(refundable) > 0) {
            throw new BadRequestException("Only " + refundable.toPlainString() + " "
                    + record.getCurrency() + " is still refundable");
        }

        RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(toMinorUnits(amount))
                .setReason(refundReason(request.reason()));

        Refund refund = call(() -> stripe.refunds().create(params.build()), "create refund");

        record.setRefundedAmount(alreadyRefunded.add(amount));
        record.setStatus(record.getRefundedAmount().compareTo(record.getAmount()) >= 0
                ? "refunded" : "partially_refunded");

        auditService.record("payment.refunded", paymentIntentId,
                Map.of("amount", amount, "refundId", refund.getId()));

        eventPublisher.publish(EventType.PAYMENT_REFUNDED, record.getCustomerEmail(),
                eventData(record, Map.of("refundedAmount", amount.toPlainString())));

        log.info("Refunded {} {} on payment {}", amount, record.getCurrency(), paymentIntentId);
        return new RefundDto(refund.getId(), paymentIntentId, amount, refund.getStatus(),
                refund.getReason());
    }

    /** Stripe only accepts a fixed set of refund reasons; anything else is mapped to the default. */
    private static RefundCreateParams.Reason refundReason(String reason) {
        if (reason == null) {
            return RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        }
        return switch (reason.toLowerCase()) {
            case "duplicate" -> RefundCreateParams.Reason.DUPLICATE;
            case "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT;
            default -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        };
    }

    @Transactional
    public PaymentIntentDto cancel(String paymentIntentId, String cancellationReason) {
        PaymentIntentCancelParams.Builder params = PaymentIntentCancelParams.builder();
        cancellationParams(cancellationReason).ifPresent(params::setCancellationReason);

        PaymentIntent intent = call(() -> stripe.paymentIntents().cancel(paymentIntentId,
                params.build()), "cancel payment intent");

        applyIntentState(intent);
        auditService.record("payment_intent.cancelled", paymentIntentId,
                Map.of("reason", String.valueOf(cancellationReason)));
        return toDto(intent);
    }

    private static java.util.Optional<PaymentIntentCancelParams.CancellationReason>
            cancellationParams(String reason) {
        if (reason == null) {
            return java.util.Optional.of(PaymentIntentCancelParams.CancellationReason.REQUESTED_BY_CUSTOMER);
        }
        return switch (reason.toLowerCase()) {
            case "duplicate" -> java.util.Optional.of(
                    PaymentIntentCancelParams.CancellationReason.DUPLICATE);
            case "fraudulent" -> java.util.Optional.of(
                    PaymentIntentCancelParams.CancellationReason.FRAUDULENT);
            case "abandoned" -> java.util.Optional.of(
                    PaymentIntentCancelParams.CancellationReason.ABANDONED);
            default -> java.util.Optional.of(
                    PaymentIntentCancelParams.CancellationReason.REQUESTED_BY_CUSTOMER);
        };
    }

    @Transactional
    public CustomerDto createCustomer(CustomerRequest request) {
        CustomerCreateParams.Builder params = CustomerCreateParams.builder()
                .setEmail(request.email().trim().toLowerCase());
        if (request.name() != null) {
            params.setName(request.name());
        }
        if (request.phone() != null) {
            params.setPhone(request.phone());
        }
        if (request.userId() != null) {
            params.putMetadata("userId", request.userId());
        }

        Customer customer = call(() -> stripe.customers().create(params.build()), "create customer");
        auditService.record("customer.created", null, Map.of("customerId", customer.getId()));
        return new CustomerDto(customer.getId(), customer.getEmail(), customer.getName(),
                customer.getPhone());
    }

    public CustomerDto getCustomer(String customerId) {
        Customer customer = call(() -> stripe.customers().retrieve(customerId), "retrieve customer");
        return new CustomerDto(customer.getId(), customer.getEmail(), customer.getName(),
                customer.getPhone());
    }

    /**
     * Payment history. Served from the local table, falling back to Stripe only when this service
     * has no record of the customer - which keeps the common case off the network.
     */
    @Transactional(readOnly = true)
    public List<PaymentDetailsDto> customerHistory(String customerId, int limit) {
        List<PaymentRecord> local = payments.findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageRequest.of(0, Math.min(limit, 100)));
        if (!local.isEmpty()) {
            return local.stream().map(PaymentService::toDetails).toList();
        }

        PaymentIntentListParams params = PaymentIntentListParams.builder()
                .setCustomer(customerId)
                .setLimit((long) Math.min(limit, 100))
                .build();
        return call(() -> stripe.paymentIntents().list(params), "list payment intents")
                .getData().stream()
                .map(intent -> new PaymentDetailsDto(intent.getId(), null, null, customerId, null,
                        fromMinorUnits(intent.getAmount()), intent.getCurrency(), intent.getStatus(),
                        BigDecimal.ZERO, null, intent.getDescription(), null, null))
                .toList();
    }

    public PaymentConfigDto config() {
        return new PaymentConfigDto(properties.getPublishableKey(), properties.getCurrency(),
                properties.getPaymentMethods(), properties.getSupportedCountries());
    }

    /**
     * Writes Stripe's view of an intent into the local record and publishes the matching event.
     * Both the webhook and the polling endpoints funnel through here, so a duplicate webhook and a
     * manual sync cannot produce different outcomes.
     */
    @Transactional
    public PaymentRecord applyIntentState(PaymentIntent intent) {
        PaymentRecord record = payments.findByPaymentIntentId(intent.getId())
                .orElseGet(() -> reconstructRecord(intent));

        String previousStatus = record.getStatus();
        record.setStatus(intent.getStatus());
        if (intent.getLastPaymentError() != null) {
            record.setFailureMessage(intent.getLastPaymentError().getMessage());
        }
        if (intent.getCustomer() != null) {
            record.setCustomerId(intent.getCustomer());
        }
        payments.save(record);

        if (!intent.getStatus().equals(previousStatus)) {
            publishStatusEvent(record);
        }
        return record;
    }

    /** A payment created outside this service (or lost with its row) still gets a record. */
    private PaymentRecord reconstructRecord(PaymentIntent intent) {
        PaymentRecord record = new PaymentRecord();
        record.setPaymentIntentId(intent.getId());
        record.setAmount(fromMinorUnits(intent.getAmount()));
        record.setCurrency(intent.getCurrency());
        record.setStatus(intent.getStatus());
        record.setCustomerId(intent.getCustomer());
        record.setDescription(intent.getDescription());
        Map<String, String> metadata = intent.getMetadata();
        if (metadata != null) {
            record.setBookingId(metadata.get("bookingId"));
            record.setFlightId(metadata.get("flightId"));
        }
        return record;
    }

    private void publishStatusEvent(PaymentRecord record) {
        EventType type = switch (record.getStatus()) {
            case "succeeded" -> EventType.PAYMENT_SUCCEEDED;
            case "canceled", "requires_payment_method" -> EventType.PAYMENT_FAILED;
            default -> null;
        };
        if (type == null) {
            return; // intermediate states are not interesting to subscribers
        }
        eventPublisher.publish(type, record.getCustomerEmail(), eventData(record, Map.of()));
    }

    private Map<String, Object> eventData(PaymentRecord record, Map<String, String> extra) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentIntentId", record.getPaymentIntentId());
        data.put("bookingId", record.getBookingId());
        data.put("amount", record.getAmount().toPlainString());
        data.put("currency", record.getCurrency());
        data.put("status", record.getStatus());
        if (record.getFailureMessage() != null) {
            data.put("failureMessage", record.getFailureMessage());
        }
        data.putAll(extra);
        return data;
    }

    // ---------------------------------------------------------------- helpers

    /** Stripe works in minor units; this is the only place the conversion happens. */
    private static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(MINOR_UNITS).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal fromMinorUnits(Long amount) {
        return amount == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(amount).divide(MINOR_UNITS, 2, RoundingMode.HALF_UP);
    }

    private static PaymentIntentDto toDto(PaymentIntent intent) {
        return new PaymentIntentDto(intent.getId(), intent.getClientSecret(),
                fromMinorUnits(intent.getAmount()), intent.getCurrency(), intent.getStatus());
    }

    private static PaymentDetailsDto toDetails(PaymentRecord record) {
        return new PaymentDetailsDto(record.getPaymentIntentId(), record.getBookingId(),
                record.getFlightId(), record.getCustomerId(), record.getCustomerEmail(),
                record.getAmount(), record.getCurrency(), record.getStatus(),
                record.getRefundedAmount(), record.getFailureMessage(), record.getDescription(),
                record.getCreatedAt(), record.getUpdatedAt());
    }

    /** Turns Stripe's checked exception into the shared upstream-failure type. */
    private <T> T call(StripeCall<T> stripeCall, String action) {
        try {
            return stripeCall.execute();
        } catch (StripeException ex) {
            log.error("Stripe call failed: {}", action, ex);
            throw new UpstreamException("Could not " + action + ": " + ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    private interface StripeCall<T> {
        T execute() throws StripeException;
    }
}
