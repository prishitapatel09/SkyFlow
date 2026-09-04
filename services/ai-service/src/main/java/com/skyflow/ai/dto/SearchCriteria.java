package com.skyflow.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured output schema for natural language search. The field descriptions are the prompt -
 * Claude fills this record directly, so there is no free-text parsing step to get wrong.
 */
public record SearchCriteria(

        @JsonPropertyDescription("Departure city name exactly as the traveller said it, or null")
        String originCity,

        @JsonPropertyDescription("Three letter IATA code for the departure airport if one can be "
                + "determined with confidence, otherwise null")
        String originAirportCode,

        @JsonPropertyDescription("Destination city name exactly as the traveller said it, or null")
        String destinationCity,

        @JsonPropertyDescription("Three letter IATA code for the destination airport if one can be "
                + "determined with confidence, otherwise null")
        String destinationAirportCode,

        @JsonPropertyDescription("Departure date as ISO-8601 yyyy-MM-dd, resolved against today's "
                + "date given in the prompt. Null if the traveller did not indicate a date")
        String departureDate,

        @JsonPropertyDescription("Number of travellers; 1 when unspecified")
        Integer passengers,

        @JsonPropertyDescription("Highest acceptable price per seat in USD, or null if no budget "
                + "was mentioned")
        Integer maxPrice,

        @JsonPropertyDescription("One of: price, departureTime, duration. Use price when the "
                + "traveller asked for cheap or budget options")
        String sortBy,

        @JsonPropertyDescription("One short sentence, addressed to the traveller, restating what "
                + "was searched for")
        String interpretation) {
}
