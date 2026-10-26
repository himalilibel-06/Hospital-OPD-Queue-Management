package com.smartopd.exception;

/**
 * InvalidSeverityException - Thrown when clinical severity is outside [1, 5]
 * or violates triage rules (e.g. Emergency patient with severity < 4).
 * 
 * ============================================================================
 * OOP Concept: Subclass Specialization of Exceptions
 * ============================================================================
 */
public class InvalidSeverityException extends SmartOpdException {
    public InvalidSeverityException(String message) {
        super(message);
    }
}
