package com.skyflow.booking.service;

import com.skyflow.booking.client.FlightClient;
import com.skyflow.booking.client.PaymentClient;
import com.skyflow.booking.domain.Booking;
import com.skyflow.booking.domain.BookingStatus;
import com.skyflow.booking.dto.CreateBookingRequest;
import com.skyflow.booking.dto.PassengerRequest;
import com.skyflow.booking.dto.PaymentIntentDto;
import com.skyflow.booking.repository.BookingRepository;
import com.skyflow.common.event.EventPublisher;
import com.skyflow.common.event.EventType;
import com.skyflow.common.exception.BadRequestException;
import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.UpstreamException;
import com.skyflow.common.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private static final AuthenticatedUser TRAVELLER =
            new AuthenticatedUser("42", "traveller@example.com", "user");

    private BookingRepository bookings;
    private FlightClient flightClient;
    private PaymentClient paymentClient;
    private EventPublisher eventPublisher;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        flightClient = mock(FlightClient.class);
        paymentClient = mock(PaymentClient.class);
        eventPublisher = mock(EventPublisher.class);
        bookingService = new BookingService(bookings, flightClient, paymentClient, eventPublisher,
                Duration.ofMinutes(15));

        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("a booking reserves seats, then creates the payment intent")
    void createsBookingAndPaymentIntent() {
        when(flightClient.getFlight(7L)).thenReturn(flight(120));
        when(paymentClient.createIntent(anyString(), any(), eq(7L), any(), anyString(), anyString()))
                .thenReturn(new PaymentIntentDto("pi_123", "pi_123_secret",
                        new BigDecimal("698.00"), "usd", "requires_payment_method"));

        var created = bookingService.create(request(7L, 2), TRAVELLER);

        verify(flightClient).reserveSeats(7L, 2);
        assertThat(created.booking().seats()).isEqualTo(2);
        assertThat(created.booking().totalAmount()).isEqualByComparingTo("698.00");
        assertThat(created.booking().status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(created.booking().bookingReference()).startsWith("SKY-");
        assertThat(created.booking().contactEmail()).isEqualTo("traveller@example.com");
        assertThat(created.payment().clientSecret()).isEqualTo("pi_123_secret");
    }

    @Test
    @DisplayName("seats are given back when the payment intent cannot be created")
    void releasesSeatsWhenPaymentSetupFails() {
        when(flightClient.getFlight(7L)).thenReturn(flight(120));
        when(paymentClient.createIntent(anyString(), any(), anyLong(), any(), anyString(), anyString()))
                .thenThrow(new UpstreamException("payment-service is unavailable"));

        assertThatThrownBy(() -> bookingService.create(request(7L, 2), TRAVELLER))
                .isInstanceOf(UpstreamException.class);

        // Without this the flight would leak two seats on every payment-service outage.
        verify(flightClient).reserveSeats(7L, 2);
        verify(flightClient).releaseSeats(7L, 2);
    }

    @Test
    @DisplayName("a booking is refused when the flight no longer has room")
    void refusesWhenSoldOut() {
        when(flightClient.getFlight(7L)).thenReturn(flight(1));

        assertThatThrownBy(() -> bookingService.create(request(7L, 2), TRAVELLER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 seat(s) left");

        verify(flightClient, never()).reserveSeats(anyLong(), anyInt());
    }

    @Test
    @DisplayName("a booking is refused once the flight has departed")
    void refusesDepartedFlights() {
        FlightClient.FlightSnapshot departed = new FlightClient.FlightSnapshot(
                7L, "SF001",
                new FlightClient.FlightSnapshot.Airport(1L, "Logan", "BOS"),
                new FlightClient.FlightSnapshot.Airport(2L, "SFO", "SFO"),
                Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofMinutes(30)),
                new BigDecimal("349.00"), 300, 120, "scheduled");
        when(flightClient.getFlight(7L)).thenReturn(departed);

        assertThatThrownBy(() -> bookingService.create(request(7L, 1), TRAVELLER))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already departed");
    }

    @Test
    @DisplayName("a repeated payment.succeeded event confirms the booking only once")
    void markPaidIsIdempotent() {
        Booking booking = pendingBooking();
        when(bookings.findByPaymentIntentId("pi_123")).thenReturn(Optional.of(booking));

        bookingService.markPaid("pi_123");
        bookingService.markPaid("pi_123");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getHoldExpiresAt()).isNull();
        verify(eventPublisher, times(1))
                .publish(eq(EventType.BOOKING_CONFIRMED), eq("traveller@example.com"), any());
    }

    @Test
    @DisplayName("a payment that lands after the hold expired is refunded, not resurrected")
    void refundsPaymentForAnExpiredHold() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.EXPIRED);
        when(bookings.findByPaymentIntentId("pi_123")).thenReturn(Optional.of(booking));

        bookingService.markPaid("pi_123");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(paymentClient).refund("pi_123", "hold_expired");
        verify(eventPublisher, never()).publish(eq(EventType.BOOKING_CONFIRMED), anyString(), any());
    }

    @Test
    @DisplayName("a failed payment releases the held seats straight away")
    void failedPaymentReleasesSeats() {
        Booking booking = pendingBooking();
        when(bookings.findByPaymentIntentId("pi_123")).thenReturn(Optional.of(booking));

        bookingService.markPaymentFailed("pi_123", "card_declined");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        verify(flightClient).releaseSeats(7L, 2);
    }

    @Test
    @DisplayName("expiring a hold is a no-op unless it really is an unpaid hold that ran out")
    void expireHoldIsGuarded() {
        Booking confirmed = pendingBooking();
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findById(1L)).thenReturn(Optional.of(confirmed));
        assertThat(bookingService.expireHold(1L)).isFalse();

        Booking notYetExpired = pendingBooking();
        notYetExpired.setHoldExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        when(bookings.findById(2L)).thenReturn(Optional.of(notYetExpired));
        assertThat(bookingService.expireHold(2L)).isFalse();

        Booking expired = pendingBooking();
        expired.setHoldExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(bookings.findById(3L)).thenReturn(Optional.of(expired));
        assertThat(bookingService.expireHold(3L)).isTrue();
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        verify(flightClient, times(1)).releaseSeats(7L, 2);
    }

    @Test
    @DisplayName("a reminder is sent once, so a re-run of the sweep does not mail twice")
    void reminderIsSentOnce() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findById(1L)).thenReturn(Optional.of(booking));

        assertThat(bookingService.sendReminder(1L)).isTrue();
        assertThat(bookingService.sendReminder(1L)).isFalse();

        verify(eventPublisher, times(1))
                .publish(eq(EventType.REMINDER), eq("traveller@example.com"), any());
        assertThat(booking.getReminderSentAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelling a paid booking releases the seats and asks for a refund")
    void cancelReleasesSeatsAndRefunds() {
        Booking booking = pendingBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.cancel(1L, "plans changed", TRAVELLER);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(flightClient).releaseSeats(7L, 2);
        verify(paymentClient).refund("pi_123", "plans changed");
        verify(eventPublisher).publish(eq(EventType.BOOKING_CANCELLED), eq("traveller@example.com"),
                any());
    }

    @Test
    @DisplayName("another traveller's booking reads as not found, not as forbidden")
    void hidesOtherPeoplesBookings() {
        Booking booking = pendingBooking();
        booking.setUserId("99");
        when(bookings.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.get(1L, TRAVELLER))
                .isInstanceOf(com.skyflow.common.exception.ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- fixtures

    private static CreateBookingRequest request(long flightId, int passengers) {
        List<PassengerRequest> travellers = passengers == 2
                ? List.of(new PassengerRequest("Ada Lovelace", null),
                          new PassengerRequest("Alan Turing", null))
                : List.of(new PassengerRequest("Ada Lovelace", null));
        return new CreateBookingRequest(flightId, travellers, null, "Ada Lovelace");
    }

    private static FlightClient.FlightSnapshot flight(int availableSeats) {
        return new FlightClient.FlightSnapshot(
                7L, "SF001",
                new FlightClient.FlightSnapshot.Airport(1L, "Logan International", "BOS"),
                new FlightClient.FlightSnapshot.Airport(2L, "San Francisco International", "SFO"),
                Instant.now().plus(Duration.ofDays(3)),
                Instant.now().plus(Duration.ofDays(3)).plus(Duration.ofHours(6)),
                new BigDecimal("349.00"), 300, availableSeats, "scheduled");
    }

    private static Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setBookingReference("SKY-7K2QD9");
        booking.setUserId("42");
        booking.setContactEmail("traveller@example.com");
        booking.setFlightId(7L);
        booking.setFlightNumber("SF001");
        booking.setOriginCode("BOS");
        booking.setDestinationCode("SFO");
        booking.setDepartureTime(Instant.now().plus(Duration.ofDays(1)));
        booking.setArrivalTime(Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(6)));
        booking.setSeats(2);
        booking.setTotalAmount(new BigDecimal("698.00"));
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentIntentId("pi_123");
        booking.setHoldExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        return booking;
    }
}
