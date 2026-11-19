package dev.randomchat.platform.common.exception;

import java.time.Instant;

/**
 * Standardized error response body sent for all API errors.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now());
    }
}
