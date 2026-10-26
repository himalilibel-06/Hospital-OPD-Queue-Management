package com.smartopd.exception;

/**
 * ConflictException - Thrown when a request conflicts with the current system or resource state (HTTP 409).
 */
public class ConflictException extends SmartOpdException {
    public ConflictException(String message) {
        super(message);
    }
}
