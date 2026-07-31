package com.yourname.aicommerce.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a refresh token is invalid, expired, or has already been used.
 * <p>
 * Mapped to HTTP 401 Unauthorized by {@code GlobalExceptionHandler}.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
