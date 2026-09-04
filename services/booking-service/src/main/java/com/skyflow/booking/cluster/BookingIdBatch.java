package com.skyflow.booking.cluster;

import java.util.List;

/** Task payload: the chunk of bookings one worker should process. */
public record BookingIdBatch(List<Long> bookingIds) {
}
