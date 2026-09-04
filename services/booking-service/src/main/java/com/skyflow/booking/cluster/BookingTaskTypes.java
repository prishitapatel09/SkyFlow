package com.skyflow.booking.cluster;

/** Task types this service's workers know how to run. */
public final class BookingTaskTypes {

    /** Release seats held by bookings whose payment window closed. */
    public static final String HOLD_EXPIRY = "booking.hold-expiry";

    /** Queue reminder emails for bookings departing inside the reminder window. */
    public static final String REMINDER = "booking.reminder";

    /** Re-run a popular search so the Redis entry is warm before travellers ask for it. */
    public static final String CACHE_WARM = "flight.cache-warm";

    private BookingTaskTypes() {
    }
}
