package com.skyflow.booking.service;

import com.skyflow.booking.client.FlightClient;
import com.skyflow.booking.client.PaymentClient;
import com.skyflow.booking.domain.Booking;
import com.skyflow.booking.domain.BookingStatus;
import com.skyflow.booking.domain.Passenger;
import com.skyflow.booking.dto.BookingCreatedDto;
import com.skyflow.booking.dto.BookingDto;
import com.skyflow.booking.dto.CreateBookingRequest;
import com.skyflow.booking.dto.PassengerRequest;
import com.skyflow.booking.dto.PaymentIntentDto;
import com.skyflow.booking.repository.BookingRepository;
import com.skyflow.common.event.EventPublisher;
import com.skyflow.common.event.EventType;
import com.skyflow.common.exception.BadRequestException;
import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.common.exception.UnauthorizedException;
import com.skyflow.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookings;
    private final FlightClient flightClient;
    private final PaymentClient paymentClient;
    private final EventPublisher eventPublisher;
    private final Duration holdDuration;

    public BookingService(BookingRepository bookings, FlightClient flightClient,
                          PaymentClient paymentClient, EventPublisher eventPublisher,
                          @Value("${skyflow.booking.hold-duration:15m}") Duration holdDuration) {
        this.bookings = bookings;
        this.flightClient = flightClient;
        this.paymentClient = paymentClient;
        this.eventPublisher = eventPublisher;
        this.holdDuration = holdDuration;
    }

    /**
     * Seats are reserved <em>before</em> the booking row is written, and released again if
     * anything after that fails. The alternative - write first, reserve later - can hand out a
     * confirmation for a flight that is already full.
     */
    @Transactional
    public BookingCreatedDto create(CreateBookingRequest request, AuthenticatedUser user) {
        FlightClient.FlightSnapshot flight = flightClient.getFlight(request.flightId());
        int seats = request.seats();

        if (!"scheduled".equalsIgnoreCase(flight.status())) {
            throw new BadRequestException("Flight " + flight.flightNumber() + " is not bookable ("
                    + flight.status() + ")");
        }
        if (flight.departureTime().isBefore(Instant.now())) {
            throw new BadRequestException("Flight " + flight.flightNumber() + " has already departed");
        }
        if (flight.availableSeats() < seats) {
            throw new ConflictException("Only " + flight.availableSeats() + " seat(s) left on flight "
                    + flight.flightNumber());
        }

        flightClient.reserveSeats(flight.id(), seats);
        try {
            Booking booking = toBooking(request, user, flight, seats);
            Booking saved = bookings.save(booking);

            PaymentIntentDto payment = paymentClient.createIntent(
                    saved.getBookingReference(), saved.getId(), flight.id(),
                    saved.getTotalAmount(), saved.getCurrency(), saved.getContactEmail());
            saved.setPaymentIntentId(payment.paymentIntentId());

            log.info("Booking {} created for flight {} ({} seat(s)), payment intent {}",
                    saved.getBookingReference(), saved.getFlightNumber(), seats,
                    payment.paymentIntentId());
            return new BookingCreatedDto(BookingMapper.toDto(saved), payment);
        } catch (RuntimeException ex) {
            flightClient.releaseSeats(flight.id(), seats);
            throw ex;
        }
    }

    private Booking toBooking(CreateBookingRequest request, AuthenticatedUser user,
                              FlightClient.FlightSnapshot flight, int seats) {
        Booking booking = new Booking();
        booking.setBookingReference(newBookingReference());
        booking.setUserId(user.id());
        booking.setContactEmail(request.contactEmail() != null && !request.contactEmail().isBlank()
                ? request.contactEmail().trim().toLowerCase()
                : user.email());
        booking.setContactName(request.contactName());
        booking.setFlightId(flight.id());
        booking.setFlightNumber(flight.flightNumber());
        booking.setOriginCode(flight.departureAirport().code());
        booking.setDestinationCode(flight.arrivalAirport().code());
        booking.setDepartureTime(flight.departureTime());
        booking.setArrivalTime(flight.arrivalTime());
        booking.setSeats(seats);
        booking.setTotalAmount(flight.price().multiply(BigDecimal.valueOf(seats)));
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setHoldExpiresAt(Instant.now().plus(holdDuration));

        if (booking.getContactEmail() == null) {
            throw new BadRequestException("contactEmail is required when the account has no email");
        }
        for (PassengerRequest passengerRequest : request.passengers()) {
            Passenger passenger = new Passenger();
            passenger.setFullName(passengerRequest.fullName().trim());
            passenger.setPassportNumber(passengerRequest.passportNumber());
            booking.addPassenger(passenger);
        }
        return booking;
    }

    private static String newBookingReference() {
        StringBuilder reference = new StringBuilder("SKY-");
        for (int i = 0; i < 6; i++) {
            reference.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
        }
        return reference.toString();
    }

    @Transactional(readOnly = true)
    public BookingDto get(Long id, AuthenticatedUser caller) {
        return BookingMapper.toDto(requireVisible(id, caller));
    }

    @Transactional(readOnly = true)
    public BookingDto getByReference(String reference, AuthenticatedUser caller) {
        Booking booking = bookings.findByBookingReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + reference));
        assertVisible(booking, caller);
        return BookingMapper.toDto(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> findMine(AuthenticatedUser caller, int page, int size) {
        Page<Booking> result = bookings.findByUserIdOrderByCreatedAtDesc(
                caller.id(), PageRequest.of(page, Math.min(size, 100)));
        return result.getContent().stream().map(BookingMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> findAll(AuthenticatedUser caller, BookingStatus status, int page, int size) {
        if (!caller.isAdmin()) {
            throw new UnauthorizedException("Administrator role required");
        }
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        Page<Booking> result = status == null
                ? bookings.findAll(pageRequest)
                : bookings.findByStatusOrderByCreatedAtDesc(status, pageRequest);
        return result.getContent().stream().map(BookingMapper::toDto).toList();
    }

    @Transactional
    public BookingDto cancel(Long id, String reason, AuthenticatedUser caller) {
        Booking booking = requireVisible(id, caller);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return BookingMapper.toDto(booking);
        }
        if (booking.getStatus() == BookingStatus.EXPIRED
                || booking.getStatus() == BookingStatus.FAILED) {
            throw new BadRequestException("Booking " + booking.getBookingReference()
                    + " is " + booking.getStatus() + " and cannot be cancelled");
        }

        boolean wasPaid = booking.getStatus() == BookingStatus.CONFIRMED;
        releaseSeatsFor(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setHoldExpiresAt(null);

        if (wasPaid && booking.getPaymentIntentId() != null) {
            paymentClient.refund(booking.getPaymentIntentId(), reason);
        }
        eventPublisher.publish(EventType.BOOKING_CANCELLED, booking.getContactEmail(),
                emailData(booking));
        log.info("Booking {} cancelled ({})", booking.getBookingReference(), reason);
        return BookingMapper.toDto(booking);
    }

    /** Called from the payment event listener when Stripe confirms the charge. */
    @Transactional
    public void markPaid(String paymentIntentId) {
        Booking booking = bookings.findByPaymentIntentId(paymentIntentId).orElse(null);
        if (booking == null) {
            log.warn("Payment {} succeeded but no booking references it", paymentIntentId);
            return;
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return; // duplicate delivery; RabbitMQ is at-least-once
        }
        if (booking.getStatus() == BookingStatus.EXPIRED) {
            // The hold ran out before the payment landed. Refund rather than resurrect a booking
            // whose seats may already have been sold to someone else.
            log.warn("Payment {} landed after booking {} expired; refunding",
                    paymentIntentId, booking.getBookingReference());
            paymentClient.refund(paymentIntentId, "hold_expired");
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setHoldExpiresAt(null);
        eventPublisher.publish(EventType.BOOKING_CONFIRMED, booking.getContactEmail(),
                emailData(booking));
        log.info("Booking {} confirmed", booking.getBookingReference());
    }

    /** Called when the payment fails: the hold is dropped immediately rather than left to expire. */
    @Transactional
    public void markPaymentFailed(String paymentIntentId, String reason) {
        Booking booking = bookings.findByPaymentIntentId(paymentIntentId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }
        releaseSeatsFor(booking);
        booking.setStatus(BookingStatus.FAILED);
        booking.setCancellationReason(reason);
        booking.setHoldExpiresAt(null);
        log.info("Booking {} failed: {}", booking.getBookingReference(), reason);
    }

    /** Idempotent: only ever releases seats for a booking that is currently holding them. */
    private void releaseSeatsFor(Booking booking) {
        if (booking.holdsSeats()) {
            flightClient.releaseSeats(booking.getFlightId(), booking.getSeats());
        }
    }

    /** Used by the hold-expiry task the master distributes across workers. */
    @Transactional
    public boolean expireHold(Long bookingId) {
        Booking booking = bookings.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        if (booking.getHoldExpiresAt() == null || booking.getHoldExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        flightClient.releaseSeats(booking.getFlightId(), booking.getSeats());
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setHoldExpiresAt(null);
        log.info("Hold on booking {} expired; {} seat(s) returned to flight {}",
                booking.getBookingReference(), booking.getSeats(), booking.getFlightNumber());
        return true;
    }

    /** Used by the reminder task; the timestamp makes a re-run a no-op. */
    @Transactional
    public boolean sendReminder(Long bookingId) {
        Booking booking = bookings.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED
                || booking.getReminderSentAt() != null) {
            return false;
        }
        eventPublisher.publish(EventType.REMINDER, booking.getContactEmail(), emailData(booking));
        booking.setReminderSentAt(Instant.now());
        return true;
    }

    private Map<String, Object> emailData(Booking booking) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingReference", booking.getBookingReference());
        data.put("flightNumber", booking.getFlightNumber());
        data.put("originCode", booking.getOriginCode());
        data.put("destinationCode", booking.getDestinationCode());
        data.put("departureTime", booking.getDepartureTime().toString());
        data.put("seats", booking.getSeats());
        data.put("totalAmount", booking.getTotalAmount().toPlainString());
        data.put("currency", booking.getCurrency());
        data.put("passengerName", booking.getContactName());
        return data;
    }

    private Booking requireVisible(Long id, AuthenticatedUser caller) {
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        assertVisible(booking, caller);
        return booking;
    }

    private void assertVisible(Booking booking, AuthenticatedUser caller) {
        if (!caller.isAdmin() && !booking.getUserId().equals(caller.id())) {
            // Deliberately "not found" rather than "forbidden": otherwise the endpoint confirms
            // that a booking id exists for someone else.
            throw new ResourceNotFoundException("Booking", booking.getId());
        }
    }
}
