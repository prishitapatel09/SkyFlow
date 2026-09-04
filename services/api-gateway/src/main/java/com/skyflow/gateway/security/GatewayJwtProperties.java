package com.skyflow.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skyflow.jwt")
public class GatewayJwtProperties {

    /** Must match the secret user-service signs with. */
    private String secret = "change-me-change-me-change-me-32b";

    private String issuer = "skyflow";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
