package dev.randomchat.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication fails (bad credentials, disabled account, etc.).
 */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
