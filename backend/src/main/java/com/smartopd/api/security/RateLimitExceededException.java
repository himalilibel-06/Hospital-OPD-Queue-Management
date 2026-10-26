package com.smartopd.api.security;

/**
 * Thrown when client IP exceeds rate limit on public tracker endpoints.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
