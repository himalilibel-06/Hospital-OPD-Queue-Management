package com.smartopd.exception;

/**
 * SmartOpdException - Base unchecked runtime exception for the SmartOPD system.
 * 
 * ============================================================================
 * OOP Concept: Custom Exception Hierarchy & Fail-Fast Robustness
 * - Extends RuntimeException to provide unchecked exception handling.
 * - Serves as the root of all domain-specific errors across the queue engine.
 * ============================================================================
 */
public class SmartOpdException extends RuntimeException {
    public SmartOpdException(String message) {
        super(message);
    }

    public SmartOpdException(String message, Throwable cause) {
        super(message, cause);
    }
}
