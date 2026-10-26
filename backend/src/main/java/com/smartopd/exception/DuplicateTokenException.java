package com.smartopd.exception;

/**
 * DuplicateTokenException - Thrown when a token collision occurs in the queue registry.
 * 
 * ============================================================================
 * OOP Concept: Unchecked Entity Collision Exception
 * ============================================================================
 */
public class DuplicateTokenException extends SmartOpdException {
    public DuplicateTokenException(String message) {
        super(message);
    }
}
