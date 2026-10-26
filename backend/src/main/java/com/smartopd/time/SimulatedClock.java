package com.smartopd.time;

import java.time.Duration;
import java.time.Instant;

/**
 * SimulatedClock - Controllable clock implementation for demo testing and simulation.
 * 
 * ============================================================================
 * OOP Concept: Encapsulation & Stateful Clock Simulation
 * - Maintains offset state to simulate time acceleration without sleeping threads.
 * ============================================================================
 */
public class SimulatedClock implements AppClock {
    private Instant baseInstant;
    private long offsetMinutes;

    public SimulatedClock() {
        this.baseInstant = Instant.now();
        this.offsetMinutes = 0;
    }

    public SimulatedClock(Instant initialInstant) {
        this.baseInstant = initialInstant != null ? initialInstant : Instant.now();
        this.offsetMinutes = 0;
    }

    @Override
    public Instant now() {
        return baseInstant.plus(Duration.ofMinutes(offsetMinutes));
    }

    /**
     * Advances the simulated clock forward by the given number of minutes.
     * 
     * @param minutes minutes to advance
     */
    public synchronized void advance(long minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("Cannot advance clock backwards in time.");
        }
        this.offsetMinutes += minutes;
    }

    /**
     * Resets the clock offset back to real-time.
     */
    public synchronized void reset() {
        this.baseInstant = Instant.now();
        this.offsetMinutes = 0;
    }

    /**
     * Explicitly sets the clock to a specific instant.
     */
    public synchronized void setNow(Instant instant) {
        this.baseInstant = instant;
        this.offsetMinutes = 0;
    }

    public long getOffsetMinutes() {
        return offsetMinutes;
    }

    public Instant getBaseInstant() {
        return baseInstant;
    }
}
