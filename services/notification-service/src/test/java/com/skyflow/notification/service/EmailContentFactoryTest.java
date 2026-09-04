package com.skyflow.notification.service;

import com.skyflow.common.event.EventType;
import com.skyflow.common.event.SkyflowEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailContentFactoryTest {

    private final EmailContentFactory factory = new EmailContentFactory();

    @Test
    @DisplayName("a confirmation names the booking and lists the flight details")
    void rendersConfirmation() {
        EmailContent content = factory.create(event(EventType.BOOKING_CONFIRMED, bookingData()));

        assertThat(content.subject()).isEqualTo("Booking SKY-7K2QD9 confirmed");
        assertThat(content.details())
                .extracting(EmailContent.Detail::label)
                .contains("Booking reference", "Flight", "Route", "Departs", "Passengers", "Total");
        assertThat(content.details())
                .extracting(EmailContent.Detail::value)
                .contains("SKY-7K2QD9", "SF001", "BOS to SFO", "349.00 USD");
    }

    @Test
    @DisplayName("a departure time is rendered for humans, not as a raw instant")
    void formatsDepartureTime() {
        EmailContent content = factory.create(event(EventType.REMINDER, bookingData()));

        assertThat(content.subject()).isEqualTo("Your flight SF001 departs tomorrow");
        assertThat(content.details())
                .extracting(EmailContent.Detail::value)
                .contains("Fri 11 Sep 2026 at 13:45 UTC");
    }

    @Test
    @DisplayName("an unparseable timestamp is shown as-is instead of dropping the row")
    void survivesABadTimestamp() {
        Map<String, Object> data = bookingData();
        data.put("departureTime", "not-a-timestamp");

        EmailContent content = factory.create(event(EventType.REMINDER, data));

        assertThat(content.details())
                .extracting(EmailContent.Detail::value)
                .contains("not-a-timestamp");
    }

    @Test
    @DisplayName("missing fields are omitted rather than rendered as null")
    void omitsMissingFields() {
        EmailContent content = factory.create(event(EventType.PAYMENT_SUCCEEDED, Map.of()));

        assertThat(content.details()).isEmpty();
        assertThat(content.toPlainText()).doesNotContain("null");
    }

    @Test
    @DisplayName("a payment failure explains why, when the reason is known")
    void includesFailureReason() {
        EmailContent content = factory.create(event(EventType.PAYMENT_FAILED,
                Map.of("failureMessage", "Your card was declined")));

        assertThat(content.intro()).contains("Your card was declined");
    }

    @Test
    @DisplayName("every event type renders, so a new type cannot silently produce no email")
    void coversEveryEventType() {
        for (EventType type : EventType.values()) {
            EmailContent content = factory.create(event(type, bookingData()));
            assertThat(content.subject()).as("subject for %s", type).isNotBlank();
            assertThat(content.heading()).as("heading for %s", type).isNotBlank();
        }
    }

    private static SkyflowEvent event(EventType type, Map<String, Object> data) {
        return SkyflowEvent.of(type, "traveller@example.com", data);
    }

    private static Map<String, Object> bookingData() {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingReference", "SKY-7K2QD9");
        data.put("flightNumber", "SF001");
        data.put("originCode", "BOS");
        data.put("destinationCode", "SFO");
        data.put("departureTime", "2026-09-11T13:45:00Z");
        data.put("seats", 2);
        data.put("totalAmount", "349.00");
        data.put("currency", "usd");
        data.put("name", "Tokyo");
        return data;
    }
}
