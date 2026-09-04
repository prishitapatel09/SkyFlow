package com.skyflow.flight.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.StringJoiner;

/**
 * Every filter the search endpoint accepts. Airports can be given by id or IATA code, and origin
 * and destination can also be given as city names - which is what both the React search form and
 * the natural language search in ai-service send.
 *
 * @param passengers minimum seats that must still be available
 */
public record FlightSearchRequest(
        Long departureAirportId,
        Long arrivalAirportId,
        String departureAirportCode,
        String arrivalAirportCode,
        String departureCity,
        String arrivalCity,
        LocalDate departureDate,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer passengers,
        String sortBy,
        Integer page,
        Integer size) {

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public int passengersOrDefault() {
        return passengers == null || passengers < 1 ? 1 : passengers;
    }

    /** Sort key: {@code price}, {@code duration} or the default {@code departureTime}. */
    public String sortByOrDefault() {
        return sortBy == null || sortBy.isBlank() ? "departureTime" : sortBy;
    }

    /**
     * Stable cache key. Built explicitly rather than from {@code toString()} so a field added
     * later cannot silently change every existing key.
     */
    public String cacheKey() {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(String.valueOf(departureAirportId));
        joiner.add(String.valueOf(arrivalAirportId));
        joiner.add(normalize(departureAirportCode));
        joiner.add(normalize(arrivalAirportCode));
        joiner.add(normalize(departureCity));
        joiner.add(normalize(arrivalCity));
        joiner.add(String.valueOf(departureDate));
        joiner.add(String.valueOf(minPrice));
        joiner.add(String.valueOf(maxPrice));
        joiner.add(String.valueOf(passengersOrDefault()));
        joiner.add(sortByOrDefault());
        joiner.add(String.valueOf(pageOrDefault()));
        joiner.add(String.valueOf(sizeOrDefault()));
        return joiner.toString();
    }

    private static String normalize(String value) {
        return value == null ? "-" : value.trim().toLowerCase();
    }
}
