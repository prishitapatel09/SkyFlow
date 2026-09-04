package com.skyflow.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Signing configuration shared by the token issuer (user-service) and every verifier. */
@ConfigurationProperties(prefix = "skyflow.jwt")
public class JwtProperties {

    /** HMAC-SHA secret. Must be at least 32 bytes; injected from a Kubernetes secret. */
    private String secret = "change-me-change-me-change-me-32b";

    /** Access token lifetime. */
    private Duration expiresIn = Duration.ofHours(24);

    /** Refresh token lifetime. */
    private Duration refreshExpiresIn = Duration.ofDays(30);

    private String issuer = "skyflow";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Duration expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Duration getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    public void setRefreshExpiresIn(Duration refreshExpiresIn) {
        this.refreshExpiresIn = refreshExpiresIn;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
