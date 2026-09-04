package com.skyflow.ai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.skyflow.ai.client.SkyflowDataClient;
import com.skyflow.ai.config.ClaudeProperties;
import com.skyflow.ai.dto.NlSearchResponse;
import com.skyflow.ai.dto.SearchCriteria;
import com.skyflow.common.exception.BadRequestException;
import com.skyflow.common.exception.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a phrase like "cheap flight from Boston to SFO next Friday for two" into the same filters
 * the search form produces, then runs the ordinary search.
 *
 * <p>Structured output does the work: Claude fills a {@link SearchCriteria} record whose schema is
 * derived from the type, so there is no prompt-and-parse step and a malformed response is a schema
 * error rather than a silently wrong search.
 */
@Service
public class NaturalLanguageSearchService {

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageSearchService.class);

    private static final String SYSTEM_PROMPT = """
            You extract flight search filters from a traveller's own words for SkyFlow, an airline
            booking platform.

            Rules:
            - Resolve relative dates ("tomorrow", "next Friday", "the 3rd") against the current date
              given in the user message. Never return a date in the past.
            - Only fill an IATA code when you are confident of it. If you are unsure, fill the city
              name instead and leave the code null.
            - Do not invent a budget, a date or a passenger count that the traveller did not state.
            - If the traveller asked for something cheap, set sortBy to price.
            """;

    private final Optional<AnthropicClient> client;
    private final ClaudeProperties properties;
    private final SkyflowDataClient dataClient;

    public NaturalLanguageSearchService(Optional<AnthropicClient> client, ClaudeProperties properties,
                                        SkyflowDataClient dataClient) {
        this.client = client;
        this.properties = properties;
        this.dataClient = dataClient;
    }

    public NlSearchResponse search(String query) {
        SearchCriteria criteria = interpret(query);
        Map<String, Object> page = dataClient.searchFlights(
                criteria.originAirportCode(), criteria.originCity(),
                criteria.destinationAirportCode(), criteria.destinationCity(),
                criteria.departureDate(), criteria.passengers(), criteria.maxPrice(),
                criteria.sortBy());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flights = (List<Map<String, Object>>) page.getOrDefault(
                "items", List.of());
        int total = totalOf(page, flights.size());

        log.info("Natural language search \"{}\" -> {} result(s)", query, total);
        return new NlSearchResponse(criteria, flights, total);
    }

    @SuppressWarnings("unchecked")
    private static int totalOf(Map<String, Object> page, int fallback) {
        Object pagination = page.get("pagination");
        if (pagination instanceof Map<?, ?> map) {
            Object total = ((Map<String, Object>) map).get("total");
            if (total instanceof Number number) {
                return number.intValue();
            }
        }
        return fallback;
    }

    SearchCriteria interpret(String query) {
        AnthropicClient anthropic = client.orElseThrow(() ->
                new UpstreamException("The AI assistant is not configured"));

        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE);

        // The schema is derived from SearchCriteria; effort rides along on the same config, since
        // this is a narrow extraction task rather than an intelligence-sensitive one.
        StructuredOutputConfig<SearchCriteria> outputConfig =
                StructuredOutputConfig.<SearchCriteria>builder()
                        .format(SearchCriteria.class, JsonSchemaLocalValidation.YES)
                        .effort(effort(properties.getExtractionEffort()))
                        .build();

        StructuredMessageCreateParams<SearchCriteria> params = MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens(properties.getMaxTokens())
                .system(SYSTEM_PROMPT)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .addUserMessage("Today is " + today + " (UTC).\n\nTraveller said: " + query)
                .outputConfig(outputConfig)
                .build();

        try {
            StructuredMessage<SearchCriteria> message = anthropic.messages().create(params);

            if (message.rawMessage().stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                throw new BadRequestException("That search could not be processed. "
                        + "Try describing the route and date directly.");
            }
            return message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new UpstreamException(
                            "The assistant returned no search criteria"));
        } catch (AnthropicServiceException ex) {
            log.error("Claude call failed while interpreting a search", ex);
            throw new UpstreamException("The AI assistant is temporarily unavailable", ex);
        }
    }

    static OutputConfig.Effort effort(String configured) {
        if (configured == null || configured.isBlank()) {
            return OutputConfig.Effort.MEDIUM;
        }
        return OutputConfig.Effort.of(configured.toLowerCase());
    }
}
