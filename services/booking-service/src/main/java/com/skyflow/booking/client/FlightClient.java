package com.skyflow.booking.client;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.common.config.ServiceEndpoints;
import com.skyflow.common.exception.ConflictException;
import com.skyflow.common.exception.ResourceNotFoundException;
import com.skyflow.common.exception.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Calls flight-service for the flight snapshot a booking copies, and for the seat reservation
 * that makes the booking valid.
 */
@Component
public class FlightClient {

    private static final Logger log = LoggerFactory.getLogger(FlightClient.class);

    /** Only the fields a booking needs; flight-service is free to add others. */
    public record FlightSnapshot(
            Long id,
            String flightNumber,
            Airport departureAirport,
            Airport arrivalAirport,
            Instant departureTime,
            Instant arrivalTime,
            BigDecimal price,
            int totalSeats,
            int availableSeats,
            String status) {

        public record Airport(Long id, String name, String code) {
        }
    }

    private final RestClient restClient;

    public FlightClient(RestClient.Builder builder, ServiceEndpoints endpoints) {
        this.restClient = builder.baseUrl(endpoints.getFlight()).build();
    }

    public FlightSnapshot getFlight(Long flightId) {
        try {
            ApiResponse<FlightSnapshot> response = restClient.get()
                    .uri("/api/v1/flights/{id}", flightId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                        throw new ResourceNotFoundException("Flight", flightId);
                    })
                    .body(new ParameterizedTypeReference<ApiResponse<FlightSnapshot>>() { });

            if (response == null || response.data() == null) {
                throw new UpstreamException("flight-service returned no flight for " + flightId);
            }
            return response.data();
        } catch (RestClientException ex) {
            throw new UpstreamException("flight-service is unavailable", ex);
        }
    }

    /**
     * Reserves seats. A 409 means the flight sold out between search and checkout, which is a
     * normal race rather than a fault, so it is surfaced as a conflict to the caller.
     */
    public void reserveSeats(Long flightId, int seats) {
        exchangeSeats(flightId, seats, "reserve");
    }

    /** Returns seats to inventory. Failures are logged, not thrown: see callers for why. */
    public void releaseSeats(Long flightId, int seats) {
        try {
            exchangeSeats(flightId, seats, "release");
        } catch (RuntimeException ex) {
            // The hold-expiry sweep retries, so losing one release is recoverable; failing the
            // surrounding cancellation would not be.
            log.error("Could not release {} seat(s) on flight {}", seats, flightId, ex);
        }
    }

    private void exchangeSeats(Long flightId, int seats, String action) {
        try {
            restClient.post()
                    .uri("/api/v1/flights/{id}/seats/{action}", flightId, action)
                    .body(Map.of("seats", seats))
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(),
                            (request, clientResponse) -> {
                                throw new ConflictException(
                                        "Not enough seats left on flight " + flightId);
                            })
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                            (request, clientResponse) -> {
                                throw new ResourceNotFoundException("Flight", flightId);
                            })
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new UpstreamException("flight-service seat " + action + " failed", ex);
        }
    }
}
