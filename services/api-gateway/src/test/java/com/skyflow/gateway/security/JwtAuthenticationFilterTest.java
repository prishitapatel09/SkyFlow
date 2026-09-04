package com.skyflow.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is the only place a token is verified, and downstream services trust the headers it
 * sets. These tests cover the two properties that has to hold: a valid token produces the identity
 * headers, and a caller can never supply them itself.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256";

    private SecretKey key;
    private JwtAuthenticationFilter filter;

    /** Captures the request the filter forwarded, so the mutated headers can be inspected. */
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        GatewayJwtProperties properties = new GatewayJwtProperties();
        properties.setSecret(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        filter = new JwtAuthenticationFilter(properties);
        forwarded.set(null);
    }

    @Test
    @DisplayName("a valid token becomes the X-User-* headers downstream services read")
    void injectsIdentityHeaders() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest
                .get("/api/v1/bookings/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("42", "a@example.com", "user")));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("a@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("user");
    }

    @Test
    @DisplayName("client-supplied identity headers are stripped, not honoured")
    void stripsSpoofedIdentityHeaders() {
        // Without this, anyone could self-promote by sending a header.
        MockServerWebExchange exchange = exchange(MockServerHttpRequest
                .get("/api/v1/bookings/my")
                .header("X-User-Id", "1")
                .header("X-User-Role", "admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("42", "a@example.com", "user")));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("user");
    }

    @Test
    @DisplayName("identity headers are stripped from anonymous requests too")
    void stripsIdentityHeadersOnPublicRoutes() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest
                .get("/api/v1/flights/search")
                .header("X-User-Id", "1")
                .header("X-User-Role", "admin"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-User-Role")).isNull();
    }

    @Test
    @DisplayName("public reads pass through without a token, writes to the same path do not")
    void publicReadsOnly() {
        MockServerWebExchange read = exchange(MockServerHttpRequest.get("/api/v1/flights/search"));
        filter.filter(read, chain).block();
        assertThat(forwarded.get()).isNotNull();

        forwarded.set(null);
        MockServerWebExchange write = exchange(MockServerHttpRequest.post("/api/v1/flights"));
        filter.filter(write, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(write.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a protected route without a token is rejected")
    void rejectsMissingToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings/my"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsForeignSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-value-32".getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder()
                .subject("1")
                .issuer("skyflow")
                .claims(Map.of("role", "admin", "typ", "access"))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(otherKey)
                .compact();

        MockServerWebExchange exchange = exchange(MockServerHttpRequest
                .get("/api/v1/bookings/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a refresh token cannot be used to call the API")
    void rejectsRefreshToken() {
        String refresh = Jwts.builder()
                .subject("42")
                .issuer("skyflow")
                .claims(Map.of("typ", "refresh"))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key)
                .compact();

        MockServerWebExchange exchange = exchange(MockServerHttpRequest
                .get("/api/v1/bookings/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject("42")
                .issuer("skyflow")
                .claims(Map.of("role", "user", "typ", "access"))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();

        MockServerWebExchange exchange = exchange(MockServerHttpRequest
                .get("/api/v1/bookings/my")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("the Stripe webhook is reachable without a token")
    void webhookIsPublic() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/webhooks/stripe"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.build());
    }

    private String accessToken(String subject, String email, String role) {
        return Jwts.builder()
                .subject(subject)
                .issuer("skyflow")
                .claims(Map.of("email", email, "role", role, "typ", "access"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key)
                .compact();
    }
}
