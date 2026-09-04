package com.skyflow.common.security;

import com.skyflow.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtProperties properties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("test-secret-that-is-long-enough-for-hs256");
        jwtService = new JwtService(properties);
    }

    @Test
    @DisplayName("an access token round-trips the caller's id, email and role")
    void issuesAndReadsAccessTokens() {
        String token = jwtService.issueAccessToken("42", "traveller@example.com", "admin");

        AuthenticatedUser user = jwtService.toUser(token);

        assertThat(user.id()).isEqualTo("42");
        assertThat(user.email()).isEqualTo("traveller@example.com");
        assertThat(user.role()).isEqualTo("admin");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("a refresh token cannot be used as an access token, or the other way round")
    void tokenTypesAreNotInterchangeable() {
        String refreshToken = jwtService.issueRefreshToken("42");
        String accessToken = jwtService.issueAccessToken("42", "traveller@example.com", "user");

        assertThat(jwtService.subjectOfRefreshToken(refreshToken)).isEqualTo("42");

        assertThatThrownBy(() -> jwtService.toUser(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("access token");
        assertThatThrownBy(() -> jwtService.subjectOfRefreshToken(accessToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("refresh token");
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsForeignSignatures() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-value-32");
        String foreignToken = new JwtService(otherProperties)
                .issueAccessToken("1", "attacker@example.com", "admin");

        assertThatThrownBy(() -> jwtService.toUser(foreignToken))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredTokens() {
        properties.setExpiresIn(Duration.ofSeconds(-1));
        String expired = new JwtService(properties)
                .issueAccessToken("42", "traveller@example.com", "user");

        assertThatThrownBy(() -> jwtService.toUser(expired))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("a token from a different issuer is rejected")
    void rejectsForeignIssuers() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret(properties.getSecret());
        otherProperties.setIssuer("somebody-else");
        String foreignToken = new JwtService(otherProperties)
                .issueAccessToken("1", "x@example.com", "user");

        assertThatThrownBy(() -> jwtService.toUser(foreignToken))
                .isInstanceOf(UnauthorizedException.class);
    }
}
