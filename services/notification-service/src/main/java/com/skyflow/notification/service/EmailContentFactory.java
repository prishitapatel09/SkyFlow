package com.skyflow.notification.service;

import com.skyflow.common.event.SkyflowEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Turns an event into subject and body copy. One place to change wording, per event type. */
@Component
public class EmailContentFactory {

    private static final DateTimeFormatter DEPARTURE_FORMAT =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy 'at' HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    public EmailContent create(SkyflowEvent event) {
        String reference = event.string("bookingReference");
        String flightNumber = event.string("flightNumber");
        String route = route(event);
        String departure = formatDeparture(event.string("departureTime"));

        return switch (event.type()) {
            case REMINDER -> new EmailContent(
                    "Your flight " + flightNumber + " departs tomorrow",
                    "See you at the airport",
                    "This is a reminder that your upcoming flight departs within 24 hours.",
                    details(reference, flightNumber, route, departure, event),
                    "Check in online to save time at the airport.");

            case BOOKING_CONFIRMED -> new EmailContent(
                    "Booking " + reference + " confirmed",
                    "Your booking is confirmed",
                    "Payment received - your seats are booked. Keep this reference handy.",
                    details(reference, flightNumber, route, departure, event),
                    "Manage your booking any time from your SkyFlow account.");

            case BOOKING_CANCELLED -> new EmailContent(
                    "Booking " + reference + " cancelled",
                    "Your booking has been cancelled",
                    "The booking below has been cancelled and the seats released. "
                            + "Any refund due is on its way back to your original payment method.",
                    details(reference, flightNumber, route, departure, event),
                    "Refunds usually take 5-10 business days to appear.");

            case PAYMENT_SUCCEEDED -> new EmailContent(
                    "Payment received",
                    "Thank you for your payment",
                    "We have received your payment for the booking below.",
                    details(reference, flightNumber, route, departure, event),
                    null);

            case PAYMENT_FAILED -> new EmailContent(
                    "We could not take your payment",
                    "Payment unsuccessful",
                    "Your payment did not go through, so the seats were not held. "
                            + (event.string("failureMessage") == null
                                ? "" : "Reason: " + event.string("failureMessage")),
                    details(reference, flightNumber, route, departure, event),
                    "You can try again from the checkout page.");

            case PAYMENT_REFUNDED -> new EmailContent(
                    "Refund issued",
                    "Your refund is on its way",
                    "We have issued a refund for the booking below.",
                    details(reference, flightNumber, route, departure, event),
                    "Refunds usually take 5-10 business days to appear.");

            case CITY_CREATED -> new EmailContent(
                    "New destination added: " + event.string("name"),
                    "New destination available",
                    "SkyFlow now serves " + event.string("name") + ".",
                    List.of(),
                    null);
        };
    }

    private List<EmailContent.Detail> details(String reference, String flightNumber, String route,
                                              String departure, SkyflowEvent event) {
        List<EmailContent.Detail> details = new ArrayList<>();
        addIfPresent(details, "Booking reference", reference);
        addIfPresent(details, "Flight", flightNumber);
        addIfPresent(details, "Route", route);
        addIfPresent(details, "Departs", departure);
        addIfPresent(details, "Passengers", event.string("seats"));
        addIfPresent(details, "Passenger name", event.string("passengerName"));

        String amount = event.string("totalAmount");
        String currency = event.string("currency");
        if (amount != null) {
            details.add(new EmailContent.Detail("Total",
                    currency == null ? amount : amount + " " + currency.toUpperCase()));
        }
        addIfPresent(details, "Refunded", event.string("refundedAmount"));
        return details;
    }

    private static void addIfPresent(List<EmailContent.Detail> details, String label, String value) {
        if (value != null && !value.isBlank()) {
            details.add(new EmailContent.Detail(label, value));
        }
    }

    private static String route(SkyflowEvent event) {
        String origin = event.string("originCode");
        String destination = event.string("destinationCode");
        return origin == null || destination == null ? null : origin + " to " + destination;
    }

    private static String formatDeparture(String isoInstant) {
        if (isoInstant == null) {
            return null;
        }
        try {
            return DEPARTURE_FORMAT.format(Instant.parse(isoInstant));
        } catch (RuntimeException ex) {
            return isoInstant; // show the raw value rather than dropping the row
        }
    }
}
