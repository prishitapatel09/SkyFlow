package com.skyflow.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends SkyflowException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
