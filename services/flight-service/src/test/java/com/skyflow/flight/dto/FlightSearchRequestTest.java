package com.skyflow.flight.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cache key decides whether a search is a hit or a miss, so it has to be stable for equivalent
 * filters and different for filters that are not.
 */
class FlightSearchRequestTest {

    @Test
    @DisplayName("filters that differ only in case or padding share a cache key")
    void normalizesTextFilters() {
        FlightSearchRequest lower = search("bos", "sfo", null);
        FlightSearchRequest mixed = new FlightSearchRequest(null, null, " BOS ", "Sfo ", null, null,
                null, null, null, null, null, null, null);

        assertThat(mixed.cacheKey()).isEqualTo(lower.cacheKey());
    }

    @Test
    @DisplayName("a different date is a different cache key")
    void dateIsPartOfTheKey() {
        assertThat(search("BOS", "SFO", LocalDate.of(2026, 9, 5)).cacheKey())
                .isNotEqualTo(search("BOS", "SFO", LocalDate.of(2026, 9, 6)).cacheKey());
    }

    @Test
    @DisplayName("an unset date is not confused with a set one")
    void nullDateIsDistinct() {
        assertThat(search("BOS", "SFO", null).cacheKey())
                .isNotEqualTo(search("BOS", "SFO", LocalDate.of(2026, 9, 5)).cacheKey());
    }

    @Test
    @DisplayName("price bounds are part of the key, so a budget search is cached separately")
    void priceBoundsArePartOfTheKey() {
        FlightSearchRequest cheap = new FlightSearchRequest(null, null, "BOS", "SFO", null, null,
                null, null, new BigDecimal("300"), null, null, null, null);

        assertThat(cheap.cacheKey()).isNotEqualTo(search("BOS", "SFO", null).cacheKey());
    }

    @Test
    @DisplayName("paging and passenger defaults are applied, and clamped where it matters")
    void appliesDefaults() {
        FlightSearchRequest empty = search(null, null, null);

        assertThat(empty.pageOrDefault()).isZero();
        assertThat(empty.sizeOrDefault()).isEqualTo(20);
        assertThat(empty.passengersOrDefault()).isEqualTo(1);
        assertThat(empty.sortByOrDefault()).isEqualTo("departureTime");

        FlightSearchRequest silly = new FlightSearchRequest(null, null, null, null, null, null, null,
                null, null, -3, "  ", -1, 5000);

        assertThat(silly.passengersOrDefault()).isEqualTo(1);
        assertThat(silly.pageOrDefault()).isZero();
        // Callers cannot ask for an unbounded page.
        assertThat(silly.sizeOrDefault()).isEqualTo(100);
        assertThat(silly.sortByOrDefault()).isEqualTo("departureTime");
    }

    private static FlightSearchRequest search(String origin, String destination, LocalDate date) {
        return new FlightSearchRequest(null, null, origin, destination, null, null, date, null, null,
                null, null, null, null);
    }
}
