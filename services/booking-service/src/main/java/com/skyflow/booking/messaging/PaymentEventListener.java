package com.skyflow.booking.messaging;

import com.skyflow.booking.service.BookingService;
import com.skyflow.common.event.RabbitTopology;
import com.skyflow.common.event.SkyflowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to payment outcomes asynchronously, which is what lets checkout return as soon as Stripe
 * has the intent instead of holding the request open until the charge settles.
 *
 * <p>Delivery is at-least-once, so every handler below is idempotent.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final BookingService bookingService;

    public PaymentEventListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @RabbitListener(queues = RabbitTopology.BOOKING_PAYMENTS_QUEUE)
    public void onPaymentEvent(SkyflowEvent event) {
        String paymentIntentId = event.string("paymentIntentId");
        if (paymentIntentId == null) {
            log.warn("Ignoring {} event without a paymentIntentId", event.type());
            return;
        }

        switch (event.type()) {
            case PAYMENT_SUCCEEDED -> bookingService.markPaid(paymentIntentId);
            case PAYMENT_FAILED -> bookingService.markPaymentFailed(paymentIntentId,
                    event.string("failureMessage"));
            case PAYMENT_REFUNDED -> log.info("Refund recorded for payment {}", paymentIntentId);
            default -> log.debug("Ignoring {} on the booking payments queue", event.type());
        }
    }
}
