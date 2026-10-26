package com.smartopd.exception;

/**
 * ResourceNotFoundException - Thrown when a requested patient, doctor, or entity is not found.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
