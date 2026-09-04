package com.skyflow.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends SkyflowException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
