package com.skyflow.ai.service;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.ai.client.SkyflowDataClient;
import com.skyflow.common.config.ServiceEndpoints;
import com.skyflow.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The assistant's tool surface, and the code that runs it.
 *
 * <p>All five tools are reads. {@code start_booking} looks like a write but is not: it returns a
 * checkout link for the traveller to open, and the booking is only created once they confirm and
 * pay. Nothing here can spend money, cancel a seat, or touch another account - identity comes from
 * the authenticated request, never from a tool argument.
 */
@Component
public class AssistantTools {

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    static final String SEARCH_FLIGHTS = "search_flights";
    static final String GET_FLIGHT = "get_flight";
    static final String LIST_AIRPORTS = "list_airports";
    static final String LIST_MY_BOOKINGS = "list_my_bookings";
    static final String GET_BOOKING = "get_booking";
    static final String START_BOOKING = "start_booking";

    private final SkyflowDataClient dataClient;
    private final ObjectMapper objectMapper;
    private final String bookingBaseUrl;

    public AssistantTools(SkyflowDataClient dataClient, ObjectMapper objectMapper,
                          ServiceEndpoints endpoints) {
        this.dataClient = dataClient;
        this.objectMapper = objectMapper;
        this.bookingBaseUrl = endpoints.getBooking();
    }

    public List<Tool> definitions() {
        return List.of(
                tool(SEARCH_FLIGHTS,
                        "Search live SkyFlow availability. Always use this before discussing "
                                + "specific flights, prices or seat availability.",
                        Map.of(
                                "originAirportCode", string("Three letter IATA code of the "
                                        + "departure airport, e.g. BOS"),
                                "originCity", string("Departure city name, if the airport code is "
                                        + "not known"),
                                "destinationAirportCode", string("Three letter IATA code of the "
                                        + "destination airport, e.g. SFO"),
                                "destinationCity", string("Destination city name, if the airport "
                                        + "code is not known"),
                                "departureDate", string("Departure date as yyyy-MM-dd"),
                                "passengers", integer("Number of travellers, default 1"),
                                "maxPrice", integer("Highest acceptable price per seat in USD"),
                                "sortBy", string("One of price, departureTime, duration")),
                        List.of()),

                tool(GET_FLIGHT,
                        "Fetch one flight in full, including current seat availability.",
                        Map.of("flightId", integer("Numeric id of the flight")),
                        List.of("flightId")),

                tool(LIST_AIRPORTS,
                        "List every airport SkyFlow serves with its IATA code and city. Use this "
                                + "to resolve a city the traveller named into an airport code.",
                        Map.of(),
                        List.of()),

                tool(LIST_MY_BOOKINGS,
                        "List the bookings belonging to the traveller you are talking to.",
                        Map.of(),
                        List.of()),

                tool(GET_BOOKING,
                        "Fetch one of the traveller's own bookings by its reference, e.g. SKY-7K2QD9.",
                        Map.of("bookingReference", string("Booking reference")),
                        List.of("bookingReference")),

                tool(START_BOOKING,
                        "Produce a checkout link for a flight the traveller has chosen. This does "
                                + "NOT create a booking or take payment - the traveller completes "
                                + "it themselves. Confirm the flight and passenger count with them "
                                + "before calling this.",
                        Map.of(
                                "flightId", integer("Numeric id of the flight to book"),
                                "passengers", integer("Number of seats, default 1")),
                        List.of("flightId")));
    }

    /** Runs one tool call and returns the JSON string handed back to the model. */
    public String execute(ToolUseBlock toolUse, AuthenticatedUser user) {
        Map<String, Object> input = inputOf(toolUse);
        log.debug("Assistant tool {} with {}", toolUse.name(), input);

        try {
            Object result = switch (toolUse.name()) {
                case SEARCH_FLIGHTS -> dataClient.searchFlights(
                        text(input, "originAirportCode"), text(input, "originCity"),
                        text(input, "destinationAirportCode"), text(input, "destinationCity"),
                        text(input, "departureDate"), number(input, "passengers"),
                        number(input, "maxPrice"), text(input, "sortBy"));
                case GET_FLIGHT -> dataClient.getFlight(input.get("flightId"));
                case LIST_AIRPORTS -> dataClient.listAirports();
                case LIST_MY_BOOKINGS -> dataClient.listMyBookings(user);
                case GET_BOOKING -> dataClient.getBookingByReference(
                        text(input, "bookingReference"), user);
                case START_BOOKING -> checkoutLink(input);
                default -> Map.of("error", "Unknown tool " + toolUse.name());
            };
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"Could not serialize the tool result\"}";
        } catch (RuntimeException ex) {
            // Returned as a tool result rather than thrown: the model can tell the traveller that
            // a lookup failed and offer to retry, which beats a 500 on the chat endpoint.
            log.warn("Assistant tool {} failed", toolUse.name(), ex);
            return "{\"error\":\"" + escape(ex.getMessage()) + "\"}";
        }
    }

    private Map<String, Object> checkoutLink(Map<String, Object> input) {
        Object flightId = input.get("flightId");
        Integer passengers = number(input, "passengers");
        return Map.of(
                "checkoutUrl", bookingBaseUrl + "/checkout?flightId=" + flightId
                        + "&passengers=" + (passengers == null ? 1 : passengers),
                "flightId", flightId,
                "passengers", passengers == null ? 1 : passengers,
                "note", "No booking has been created. The traveller must open this link, enter "
                        + "passenger details and pay.");
    }

    // ---------------------------------------------------------------- schema helpers

    private static Tool tool(String name, String description, Map<String, Object> properties,
                             List<String> required) {
        Tool.InputSchema.Properties.Builder schemaProperties = Tool.InputSchema.Properties.builder();
        properties.forEach((key, value) -> schemaProperties.putAdditionalProperty(key,
                JsonValue.from(value)));

        return Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(schemaProperties.build())
                        .required(required)
                        .build())
                .build();
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    // ---------------------------------------------------------------- input helpers

    /**
     * Tool inputs are always parsed as JSON rather than string-matched: models legitimately vary
     * their escaping, and raw matching breaks on it.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> inputOf(ToolUseBlock toolUse) {
        Object converted = toolUse._input().convert(Map.class);
        return converted == null ? Map.of() : (Map<String, Object>) converted;
    }

    private static String text(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static Integer number(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(input, key);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String escape(String message) {
        return message == null ? "unknown error" : message.replace("\"", "'").replace("\n", " ");
    }
}
