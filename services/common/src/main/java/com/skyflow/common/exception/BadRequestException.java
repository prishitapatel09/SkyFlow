package com.skyflow.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends SkyflowException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
