package com.skyflow.common.security;

/** Caller identity, either decoded from a JWT or read from the gateway-injected headers. */
public record AuthenticatedUser(String id, String email, String role) {

    public static final String HEADER_ID = "X-User-Id";
    public static final String HEADER_EMAIL = "X-User-Email";
    public static final String HEADER_ROLE = "X-User-Role";

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}
