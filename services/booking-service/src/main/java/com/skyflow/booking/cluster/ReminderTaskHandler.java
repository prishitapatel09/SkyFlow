package com.skyflow.booking.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.booking.service.BookingService;
import com.skyflow.cluster.ClusterTask;
import com.skyflow.cluster.ClusterTaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Publishes the reminder events notification-service turns into email. The work is queued rather
 * than done inline so a 10,000-booking sweep spreads across every replica instead of pinning one.
 */
@Component
public class ReminderTaskHandler implements ClusterTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ReminderTaskHandler.class);

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public ReminderTaskHandler(BookingService bookingService, ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return BookingTaskTypes.REMINDER;
    }

    @Override
    public void handle(ClusterTask task) throws Exception {
        BookingIdBatch batch = objectMapper.readValue(task.payloadJson(), BookingIdBatch.class);
        int queued = 0;
        for (Long bookingId : batch.bookingIds()) {
            if (bookingService.sendReminder(bookingId)) {
                queued++;
            }
        }
        log.info("Reminder task {} queued {} of {} email(s)",
                task.id(), queued, batch.bookingIds().size());
    }
}
