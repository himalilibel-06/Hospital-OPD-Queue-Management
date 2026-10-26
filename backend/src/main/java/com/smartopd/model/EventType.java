package com.smartopd.model;

/**
 * EventType - Audit log event categories for queue transitions.
 * 
 * ============================================================================
 * OOP Concept: Event Model Categorization
 * ============================================================================
 */
public enum EventType {
    REGISTERED,
    EMERGENCY_OVERRIDE,
    CALLED,
    COMPLETED,
    NO_SHOW,
    SKIPPED,
    MOVED_UP,
    MOVED_DOWN,
    FAIRNESS_BOOST,
    CONFIG_CHANGE
}
