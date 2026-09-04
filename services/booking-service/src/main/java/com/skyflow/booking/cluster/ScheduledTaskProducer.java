package com.skyflow.booking.cluster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.booking.repository.BookingRepository;
import com.skyflow.cluster.MasterTaskDistributor;
import com.skyflow.cluster.RaftCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Produces the periodic work the cluster distributes.
 *
 * <p>Every replica runs these schedules, and every replica except the leader returns immediately -
 * that leadership check is the whole reason for the election. Without it, a three-replica
 * Deployment would run each sweep three times and mail every traveller three reminders.
 */
@Component
public class ScheduledTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskProducer.class);

    /** Bookings per task. Small enough to spread work, large enough to amortise the round trip. */
    private static final int BATCH_SIZE = 25;

    private final RaftCoordinator raft;
    private final MasterTaskDistributor distributor;
    private final BookingRepository bookings;
    private final ObjectMapper objectMapper;

    public ScheduledTaskProducer(RaftCoordinator raft, MasterTaskDistributor distributor,
                                 BookingRepository bookings, ObjectMapper objectMapper) {
        this.raft = raft;
        this.distributor = distributor;
        this.bookings = bookings;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${skyflow.booking.hold-sweep-interval:60s}")
    @Transactional(readOnly = true)
    public void queueExpiredHolds() {
        if (!raft.isLeader()) {
            return;
        }
        List<Long> expired = bookings.findExpiredHoldIds(Instant.now());
        int queued = queueInBatches(BookingTaskTypes.HOLD_EXPIRY, expired);
        if (queued > 0) {
            log.info("Queued {} hold-expiry task(s) for {} booking(s)", queued, expired.size());
        }
    }

    @Scheduled(fixedDelayString = "${skyflow.booking.reminder-sweep-interval:15m}")
    @Transactional(readOnly = true)
    public void queueDepartureReminders() {
        if (!raft.isLeader()) {
            return;
        }
        Instant from = Instant.now().plus(Duration.ofHours(23));
        Instant to = Instant.now().plus(Duration.ofHours(25));

        List<Long> due = bookings.findBookingIdsNeedingReminder(from, to);
        int queued = queueInBatches(BookingTaskTypes.REMINDER, due);
        if (queued > 0) {
            log.info("Queued {} reminder task(s) for {} booking(s)", queued, due.size());
        }
    }

    @Scheduled(fixedDelayString = "${skyflow.booking.cache-warm-interval:10m}")
    @Transactional(readOnly = true)
    public void queueCacheWarmups() {
        if (!raft.isLeader()) {
            return;
        }
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        int queued = 0;
        for (Object[] route : bookings.findActiveRoutes(Instant.now())) {
            CacheWarmTaskHandler.RouteWarmup warmup = new CacheWarmTaskHandler.RouteWarmup(
                    (String) route[0], (String) route[1], tomorrow);
            if (submit(BookingTaskTypes.CACHE_WARM, warmup)) {
                queued++;
            }
        }
        if (queued > 0) {
            log.debug("Queued {} cache-warm task(s)", queued);
        }
    }

    private int queueInBatches(String type, List<Long> bookingIds) {
        int queued = 0;
        for (int start = 0; start < bookingIds.size(); start += BATCH_SIZE) {
            List<Long> batch = bookingIds.subList(start, Math.min(start + BATCH_SIZE, bookingIds.size()));
            if (submit(type, new BookingIdBatch(List.copyOf(batch)))) {
                queued++;
            }
        }
        return queued;
    }

    private boolean submit(String type, Object payload) {
        try {
            return distributor.submitIfLeader(type, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize payload for {} task", type, ex);
            return false;
        }
    }
}
