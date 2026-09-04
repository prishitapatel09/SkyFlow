package com.skyflow.booking.domain;

/** Lifecycle of a booking. Seats are held from {@link #PENDING_PAYMENT} onwards. */
public enum BookingStatus {

    /** Seats reserved, waiting for the payment to be confirmed. */
    PENDING_PAYMENT,

    /** Payment succeeded. */
    CONFIRMED,

    /** The hold ran out before payment completed; seats were returned to inventory. */
    EXPIRED,

    /** Cancelled by the traveller or an administrator. */
    CANCELLED,

    /** The payment failed outright. */
    FAILED
}
