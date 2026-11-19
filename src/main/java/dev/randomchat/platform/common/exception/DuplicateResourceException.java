package dev.randomchat.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource already exists (e.g., duplicate email or username).
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public DuplicateResourceException(String resourceType, String field, String value) {
        super(resourceType + " with " + field + " '" + value + "' already exists", HttpStatus.CONFLICT);
    }
}
