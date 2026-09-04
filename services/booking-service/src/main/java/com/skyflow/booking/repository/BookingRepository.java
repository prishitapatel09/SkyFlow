package com.skyflow.booking.repository;

import com.skyflow.booking.domain.Booking;
import com.skyflow.booking.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    Optional<Booking> findByPaymentIntentId(String paymentIntentId);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);

    /** Unpaid holds that have run out - the input to the hold-expiry sweep. */
    @Query("""
            select b.id from Booking b
            where b.status = com.skyflow.booking.domain.BookingStatus.PENDING_PAYMENT
              and b.holdExpiresAt < :now
            order by b.holdExpiresAt
            """)
    List<Long> findExpiredHoldIds(@Param("now") Instant now);

    /** Confirmed bookings departing inside the reminder window that have not been mailed yet. */
    @Query("""
            select b.id from Booking b
            where b.status = com.skyflow.booking.domain.BookingStatus.CONFIRMED
              and b.reminderSentAt is null
              and b.departureTime between :from and :to
            order by b.departureTime
            """)
    List<Long> findBookingIdsNeedingReminder(@Param("from") Instant from, @Param("to") Instant to);

    /** Distinct upcoming routes, used to pre-warm the flight search cache. */
    @Query("""
            select distinct b.originCode, b.destinationCode from Booking b
            where b.departureTime > :now
            """)
    List<Object[]> findActiveRoutes(@Param("now") Instant now);
}
