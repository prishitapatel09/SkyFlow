package com.skyflow.common.exception;

import org.springframework.http.HttpStatus;

/** A dependency (another SkyFlow service, Stripe, Claude, ...) failed or was unreachable. */
public class UpstreamException extends SkyflowException {

    public UpstreamException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }

    public UpstreamException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
