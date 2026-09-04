package com.skyflow.booking.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.booking.service.BookingService;
import com.skyflow.cluster.ClusterTask;
import com.skyflow.cluster.ClusterTaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Returns seats from bookings that were never paid for.
 *
 * <p>Idempotent by construction: {@code expireHold} only acts on a booking still in
 * {@code PENDING_PAYMENT} with a deadline in the past, so a task re-run after a worker failover
 * releases nothing twice.
 */
@Component
public class HoldExpiryTaskHandler implements ClusterTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryTaskHandler.class);

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public HoldExpiryTaskHandler(BookingService bookingService, ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return BookingTaskTypes.HOLD_EXPIRY;
    }

    @Override
    public void handle(ClusterTask task) throws Exception {
        BookingIdBatch batch = objectMapper.readValue(task.payloadJson(), BookingIdBatch.class);
        int expired = 0;
        for (Long bookingId : batch.bookingIds()) {
            if (bookingService.expireHold(bookingId)) {
                expired++;
            }
        }
        log.info("Hold expiry task {} released {} of {} booking(s)",
                task.id(), expired, batch.bookingIds().size());
    }
}
