package com.skyflow.common.event;

/** Names of the RabbitMQ objects shared by every service. */
public final class RabbitTopology {

    /** Topic exchange every domain event is published to. */
    public static final String EXCHANGE = "skyflow.events";

    /** Dead-letter exchange for messages that exhausted their retries. */
    public static final String DLX = "skyflow.events.dlx";

    /** Queue consumed by notification-service. */
    public static final String NOTIFICATIONS_QUEUE = "skyflow.notifications";
    public static final String NOTIFICATIONS_DLQ = "skyflow.notifications.dlq";
    public static final String NOTIFICATIONS_DLQ_KEY = "notifications.dead";

    /** Queue consumed by booking-service to react to payment outcomes. */
    public static final String BOOKING_PAYMENTS_QUEUE = "skyflow.booking.payments";

    public static final String KEY_NOTIFICATION_ALL = "notification.#";
    public static final String KEY_BOOKING_ALL = "booking.#";
    public static final String KEY_PAYMENT_ALL = "payment.#";

    public static final String KEY_REMINDER = "notification.reminder";
    public static final String KEY_BOOKING_CONFIRMED = "booking.confirmed";
    public static final String KEY_BOOKING_CANCELLED = "booking.cancelled";
    public static final String KEY_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String KEY_PAYMENT_FAILED = "payment.failed";
    public static final String KEY_PAYMENT_REFUNDED = "payment.refunded";
    public static final String KEY_CITY_CREATED = "notification.city.created";

    private RabbitTopology() {
    }
}
