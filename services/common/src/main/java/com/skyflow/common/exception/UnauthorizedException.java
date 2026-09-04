package com.skyflow.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends SkyflowException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
