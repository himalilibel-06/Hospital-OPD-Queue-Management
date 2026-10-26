package com.smartopd.exception;

/**
 * SlotFullException - Thrown when queue capacity or reserved buffer slots are exceeded.
 * 
 * ============================================================================
 * OOP Concept: Capacity Limit Domain Exception
 * ============================================================================
 */
public class SlotFullException extends SmartOpdException {
    public SlotFullException(String message) {
        super(message);
    }
}
