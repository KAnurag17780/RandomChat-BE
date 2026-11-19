package dev.randomchat.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType + " not found: " + identifier, HttpStatus.NOT_FOUND);
    }
}
