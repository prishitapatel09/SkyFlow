package com.skyflow.common.security;

import com.skyflow.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/** Issues and verifies the HS256 access/refresh tokens used across the platform. */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(String userId, String email, String role) {
        return issue(userId, Map.of(CLAIM_EMAIL, email, CLAIM_ROLE, role, CLAIM_TYPE, TYPE_ACCESS),
                properties.getExpiresIn().toSeconds());
    }

    public String issueRefreshToken(String userId) {
        return issue(userId, Map.of(CLAIM_TYPE, TYPE_REFRESH), properties.getRefreshExpiresIn().toSeconds());
    }

    private String issue(String subject, Map<String, ?> claims, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(properties.getIssuer())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** @throws UnauthorizedException when the token is malformed, expired or wrongly signed. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    public AuthenticatedUser toUser(String token) {
        Claims claims = parse(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new UnauthorizedException("Not an access token");
        }
        return new AuthenticatedUser(
                claims.getSubject(),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_ROLE, String.class));
    }

    public String subjectOfRefreshToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new UnauthorizedException("Not a refresh token");
        }
        return claims.getSubject();
    }

    public long accessTokenTtlSeconds() {
        return properties.getExpiresIn().toSeconds();
    }
}
