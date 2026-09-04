package com.skyflow.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Verifies the bearer token once, at the edge, and turns it into the {@code X-User-*} headers every
 * downstream service trusts.
 *
 * <p>Two details carry the security of the whole scheme:
 * <ul>
 *   <li>inbound {@code X-User-*} headers are stripped unconditionally, before anything else - a
 *       client that sends its own {@code X-User-Role: admin} must not be able to reach a service
 *       with it;</li>
 *   <li>services are only reachable through this gateway (a ClusterIP service plus a NetworkPolicy
 *       in Kubernetes), so nothing can bypass this filter and inject those headers directly.</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER_ID = "X-User-Id";
    private static final String HEADER_EMAIL = "X-User-Email";
    private static final String HEADER_ROLE = "X-User-Role";
    private static final String BEARER_PREFIX = "Bearer ";

    /** Reachable without a token. Everything else needs one. */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/ai/search",
            "/api/v1/webhooks/",
            "/actuator/");

    /** Public for reads only; creating or editing schedule data still needs a token. */
    private static final Set<String> PUBLIC_READ_PREFIXES = Set.of(
            "/api/v1/flights",
            "/api/v1/cities",
            "/api/v1/airports",
            "/api/v1/airplanes");

    private final SecretKey key;
    private final GatewayJwtProperties properties;

    public JwtAuthenticationFilter(GatewayJwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            if (isPublic(path, exchange.getRequest().getMethod())) {
                return chain.filter(anonymous(exchange));
            }
            return reject(exchange, "Authentication required");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();

            if (!"access".equals(claims.get("typ", String.class))) {
                return reject(exchange, "A refresh token cannot be used to call the API");
            }

            String role = claims.get("role", String.class);
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(headers -> {
                        stripIdentityHeaders(headers);
                        headers.set(HEADER_ID, claims.getSubject());
                        String email = claims.get("email", String.class);
                        if (email != null) {
                            headers.set(HEADER_EMAIL, email);
                        }
                        headers.set(HEADER_ROLE, role == null ? "user" : role);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(request).build());

        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected a request to {} with an invalid token: {}", path, ex.getMessage());
            return reject(exchange, "Invalid or expired token");
        }
    }

    /** Removes any identity headers the caller supplied, leaving the request unauthenticated. */
    private ServerWebExchange anonymous(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(JwtAuthenticationFilter::stripIdentityHeaders)
                .build();
        return exchange.mutate().request(request).build();
    }

    private static void stripIdentityHeaders(HttpHeaders headers) {
        headers.remove(HEADER_ID);
        headers.remove(HEADER_EMAIL);
        headers.remove(HEADER_ROLE);
    }

    private static boolean isPublic(String path, HttpMethod method) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        if (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method)
                || HttpMethod.OPTIONS.equals(method)) {
            for (String prefix : PUBLIC_READ_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"success":false,"message":"%s","err":"Unauthorized"}""".formatted(message);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /** Runs before routing so no downstream filter sees unverified identity headers. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
