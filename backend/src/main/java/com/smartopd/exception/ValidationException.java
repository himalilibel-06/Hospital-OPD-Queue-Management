package com.smartopd.exception;

/**
 * ValidationException - Thrown when user inputs, registration forms,
 * phone numbers, ages, or settings fail business validation rules.
 * 
 * ============================================================================
 * OOP Concept: Input Guard & Fail-Fast Validation
 * ============================================================================
 */
public class ValidationException extends SmartOpdException {
    public ValidationException(String message) {
        super(message);
    }
}
