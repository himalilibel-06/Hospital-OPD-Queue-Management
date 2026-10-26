package com.smartopd.exception;

/**
 * DoctorUnavailableException - Thrown when attempting an action on a doctor
 * who is Off Duty, On Break, or already consulting another patient.
 * 
 * ============================================================================
 * OOP Concept: Business Rule Domain Exception
 * ============================================================================
 */
public class DoctorUnavailableException extends SmartOpdException {
    public DoctorUnavailableException(String message) {
        super(message);
    }
}
