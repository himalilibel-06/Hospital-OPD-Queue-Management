package com.smartopd.api.security;

/**
 * AccountLockedException - Thrown when brute-force threshold is exceeded.
 */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
