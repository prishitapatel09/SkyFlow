package com.skyflow.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limit keys. Replaces the in-process {@code express-rate-limit} middleware with a
 * Redis-backed limiter, so the budget is shared across gateway replicas instead of being
 * multiplied by them.
 */
@Configuration
public class RateLimitConfig {

    /** Signed-in callers get their own budget; anonymous traffic is limited per client address. */
    @Bean
    public KeyResolver userOrAddressKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + (exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
        };
    }
}
