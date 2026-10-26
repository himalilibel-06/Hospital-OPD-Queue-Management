package com.smartopd.time;

import java.time.Instant;

/**
 * AppClock - Contract for querying the current system or simulated time.
 * 
 * ============================================================================
 * OOP Concept: Interface & Dependency Inversion Principle (DIP)
 * - Decouples domain time logic from real-world hardware clock.
 * - Allows deterministic automated unit testing and demo fast-forwarding.
 * ============================================================================
 */
public interface AppClock {
    /**
     * Returns the current instant in time.
     * 
     * @return current Instant
     */
    Instant now();
}
