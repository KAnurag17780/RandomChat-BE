package dev.randomchat.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for application-level errors.
 * Carries an HTTP status for consistent error response formatting.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public ApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
