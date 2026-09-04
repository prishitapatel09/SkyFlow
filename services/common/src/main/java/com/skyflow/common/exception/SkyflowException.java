package com.skyflow.common.exception;

import org.springframework.http.HttpStatus;

/** Base class for errors that carry their own HTTP status. */
public abstract class SkyflowException extends RuntimeException {

    private final HttpStatus status;

    protected SkyflowException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    protected SkyflowException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
