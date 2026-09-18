package dev.tylerpac.backend.dayz.service;

import org.springframework.http.HttpStatus;

public class DayzApiException extends RuntimeException {

    private final HttpStatus status;

    public DayzApiException(HttpStatus status, String code) {
        super(code, null, false, false);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
