package com.skyflow.booking.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflow.cluster.ClusterTask;
import com.skyflow.cluster.ClusterTaskHandler;
import com.skyflow.common.config.ServiceEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * Replays a popular search against flight-service so its Redis entry is populated before real
 * traffic asks for it. Distributing these across workers keeps a warm-up burst from competing with
 * live requests on a single replica.
 */
@Component
public class CacheWarmTaskHandler implements ClusterTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmTaskHandler.class);

    /** Task payload: one route/date combination to pre-warm. */
    public record RouteWarmup(String origin, String destination, LocalDate date) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CacheWarmTaskHandler(RestClient.Builder builder, ServiceEndpoints endpoints,
                                ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(endpoints.getFlight()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return BookingTaskTypes.CACHE_WARM;
    }

    @Override
    public void handle(ClusterTask task) throws Exception {
        RouteWarmup warmup = objectMapper.readValue(task.payloadJson(), RouteWarmup.class);

        restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/flights/search")
                        .queryParam("departureAirportCode", warmup.origin())
                        .queryParam("arrivalAirportCode", warmup.destination())
                        .queryParam("departureDate", warmup.date().toString())
                        .build())
                .retrieve()
                .toBodilessEntity();

        log.debug("Warmed search cache for {} -> {} on {}",
                warmup.origin(), warmup.destination(), warmup.date());
    }
}
