package com.skyflow.flight.repository;

import com.skyflow.flight.domain.Flight;
import com.skyflow.flight.dto.FlightSearchRequest;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed replacement for the Sequelize {@code #createFilter} helper: each filter is only applied
 * when it was actually supplied, and the price range no longer silently drops its predicates.
 */
public final class FlightSpecifications {

    private FlightSpecifications() {
    }

    public static Specification<Flight> matching(FlightSearchRequest request) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.departureAirportId() != null) {
                predicates.add(builder.equal(root.get("departureAirport").get("id"),
                        request.departureAirportId()));
            }
            if (request.arrivalAirportId() != null) {
                predicates.add(builder.equal(root.get("arrivalAirport").get("id"),
                        request.arrivalAirportId()));
            }
            if (hasText(request.departureAirportCode())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("departureAirport").get("code")),
                        request.departureAirportCode().trim().toLowerCase()));
            }
            if (hasText(request.arrivalAirportCode())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("arrivalAirport").get("code")),
                        request.arrivalAirportCode().trim().toLowerCase()));
            }
            if (hasText(request.departureCity())) {
                Join<Object, Object> city = root.join("departureAirport").join("city");
                predicates.add(builder.equal(builder.lower(city.get("name")),
                        request.departureCity().trim().toLowerCase()));
            }
            if (hasText(request.arrivalCity())) {
                Join<Object, Object> city = root.join("arrivalAirport").join("city");
                predicates.add(builder.equal(builder.lower(city.get("name")),
                        request.arrivalCity().trim().toLowerCase()));
            }
            if (request.departureDate() != null) {
                LocalDate date = request.departureDate();
                Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(builder.greaterThanOrEqualTo(root.get("departureTime"), dayStart));
                predicates.add(builder.lessThan(root.get("departureTime"), dayEnd));
            }
            if (request.minPrice() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("price"), request.minPrice()));
            }
            if (request.maxPrice() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("price"), request.maxPrice()));
            }
            predicates.add(builder.greaterThanOrEqualTo(root.get("availableSeats"),
                    request.passengersOrDefault()));

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
