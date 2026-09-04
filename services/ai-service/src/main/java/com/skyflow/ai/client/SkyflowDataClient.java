package com.skyflow.ai.client;

import com.skyflow.common.api.ApiResponse;
import com.skyflow.common.config.ServiceEndpoints;
import com.skyflow.common.exception.UpstreamException;
import com.skyflow.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * The assistant's only window onto real data. Every tool call goes through one of these methods,
 * which is what keeps the model from answering questions about flights or bookings from memory.
 *
 * <p>Reads are all the assistant can do. Booking, cancelling and refunding stay in the traveller's
 * hands: the assistant can prepare a checkout link, but a model does not get to spend someone's
 * money or give away their seat.
 */
@Component
public class SkyflowDataClient {

    private static final Logger log = LoggerFactory.getLogger(SkyflowDataClient.class);
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> LIST_RESPONSE =
            new ParameterizedTypeReference<>() { };

    private final RestClient flightClient;
    private final RestClient bookingClient;

    public SkyflowDataClient(RestClient.Builder builder, ServiceEndpoints endpoints) {
        this.flightClient = builder.clone().baseUrl(endpoints.getFlight()).build();
        this.bookingClient = builder.clone().baseUrl(endpoints.getBooking()).build();
    }

    /** Flight search. Filters map 1:1 onto flight-service's query parameters. */
    public Map<String, Object> searchFlights(String originCode, String originCity,
                                             String destinationCode, String destinationCity,
                                             String departureDate, Integer passengers,
                                             Integer maxPrice, String sortBy) {
        try {
            ApiResponse<Map<String, Object>> response = flightClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v1/flights/search");
                        addIfPresent(uriBuilder, "departureAirportCode", originCode);
                        addIfPresent(uriBuilder, "arrivalAirportCode", destinationCode);
                        addIfPresent(uriBuilder, "departureCity", originCity);
                        addIfPresent(uriBuilder, "arrivalCity", destinationCity);
                        addIfPresent(uriBuilder, "departureDate", departureDate);
                        addIfPresent(uriBuilder, "passengers", passengers);
                        addIfPresent(uriBuilder, "maxPrice", maxPrice);
                        addIfPresent(uriBuilder, "sortBy", sortBy);
                        uriBuilder.queryParam("size", 10);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(MAP_RESPONSE);
            return response == null || response.data() == null ? Map.of() : response.data();
        } catch (RestClientException ex) {
            throw new UpstreamException("flight-service search failed", ex);
        }
    }

    private static void addIfPresent(org.springframework.web.util.UriBuilder uriBuilder,
                                     String name, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            uriBuilder.queryParam(name, value);
        }
    }

    public Map<String, Object> getFlight(Object flightId) {
        try {
            ApiResponse<Map<String, Object>> response = flightClient.get()
                    .uri("/api/v1/flights/{id}", flightId)
                    .retrieve()
                    .body(MAP_RESPONSE);
            return response == null || response.data() == null ? Map.of() : response.data();
        } catch (RestClientException ex) {
            log.warn("Could not load flight {} for the assistant", flightId);
            return Map.of("error", "Flight " + flightId + " could not be found");
        }
    }

    public List<Map<String, Object>> listAirports() {
        try {
            ApiResponse<List<Map<String, Object>>> response = flightClient.get()
                    .uri("/api/v1/airports")
                    .retrieve()
                    .body(LIST_RESPONSE);
            return response == null || response.data() == null ? List.of() : response.data();
        } catch (RestClientException ex) {
            throw new UpstreamException("flight-service airport lookup failed", ex);
        }
    }

    /**
     * The caller's own bookings. Identity is taken from the authenticated request rather than from
     * anything the model produced, so the assistant cannot be talked into reading another account.
     */
    public List<Map<String, Object>> listMyBookings(AuthenticatedUser user) {
        try {
            ApiResponse<List<Map<String, Object>>> response = bookingClient.get()
                    .uri("/api/v1/bookings/my")
                    .headers(headers -> applyIdentity(headers, user))
                    .retrieve()
                    .body(LIST_RESPONSE);
            return response == null || response.data() == null ? List.of() : response.data();
        } catch (RestClientException ex) {
            throw new UpstreamException("booking-service lookup failed", ex);
        }
    }

    public Map<String, Object> getBookingByReference(String reference, AuthenticatedUser user) {
        try {
            ApiResponse<Map<String, Object>> response = bookingClient.get()
                    .uri("/api/v1/bookings/reference/{reference}", reference)
                    .headers(headers -> applyIdentity(headers, user))
                    .retrieve()
                    .body(MAP_RESPONSE);
            return response == null || response.data() == null ? Map.of() : response.data();
        } catch (RestClientException ex) {
            return Map.of("error", "No booking found with reference " + reference
                    + " on this account");
        }
    }

    private static void applyIdentity(org.springframework.http.HttpHeaders headers,
                                      AuthenticatedUser user) {
        headers.set(AuthenticatedUser.HEADER_ID, user.id());
        if (user.email() != null) {
            headers.set(AuthenticatedUser.HEADER_EMAIL, user.email());
        }
        headers.set(AuthenticatedUser.HEADER_ROLE, user.role() == null ? "user" : user.role());
    }
}
