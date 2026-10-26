package com.smartopd.time;

import java.time.Instant;

/**
 * SystemAppClock - Real-world system clock implementation.
 * 
 * ============================================================================
 * OOP Concept: Interface Implementation / Polymorphism
 * ============================================================================
 */
public class SystemAppClock implements AppClock {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
