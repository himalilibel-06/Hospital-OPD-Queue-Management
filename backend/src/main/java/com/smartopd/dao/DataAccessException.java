package com.smartopd.dao;

import com.smartopd.exception.SmartOpdException;

/**
 * DataAccessException - Unchecked domain exception wrapping low-level JDBC and SQL errors.
 * 
 * ============================================================================
 * OOP Concept: Custom Exception Hierarchy & Fail-Safe Encapsulation
 * - Shields higher layers (service, UI, tests) from checked SQLExceptions.
 * ============================================================================
 */
public class DataAccessException extends SmartOpdException {
    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(Throwable cause) {
        super(cause != null ? cause.getMessage() : "Database access error", cause);
    }
}
