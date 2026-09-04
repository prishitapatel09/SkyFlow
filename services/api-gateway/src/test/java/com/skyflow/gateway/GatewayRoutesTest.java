package com.skyflow.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the gateway and asserts the routing table actually loaded. Route configuration is YAML,
 * so a typo or a stale property namespace fails silently at runtime - this catches that at build
 * time instead.
 */
// A real reactive server on a free port: Gateway's Netty configuration needs ServerProperties,
// which a NONE web environment does not provide.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutesTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @DisplayName("every service has a route, and the webhook has its own")
    void routesAreConfigured() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();
        assertThat(routes).extracting(RouteDefinition::getId)
                .containsExactlyInAnyOrder(
                        "user-service",
                        "flight-service",
                        "booking-service",
                        "payment-service",
                        "payment-webhooks",
                        "notification-service",
                        "ai-service");
    }

    @Test
    @DisplayName("the AI route carries its own tighter rate limit")
    void aiRouteIsRateLimitedSeparately() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();
        assertThat(routes).isNotNull();

        RouteDefinition ai = routes.stream()
                .filter(route -> "ai-service".equals(route.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(ai.getFilters()).extracting(filter -> filter.getName())
                .contains("RequestRateLimiter");
    }
}
