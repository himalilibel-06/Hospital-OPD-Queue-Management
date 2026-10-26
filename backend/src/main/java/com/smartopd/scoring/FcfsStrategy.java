package com.smartopd.scoring;

import com.smartopd.config.Settings;
import com.smartopd.model.Patient;
import java.time.Duration;
import java.time.Instant;

/**
 * FcfsStrategy - First-Come, First-Served baseline policy.
 * 
 * ============================================================================
 * OOP Concept: Strategy Pattern Implementation
 * - Orders patients strictly chronologically by registration time.
 * ============================================================================
 */
public class FcfsStrategy implements PriorityStrategy {

    @Override
    public double calculateScore(Patient patient, Instant now, Settings settings) {
        if (patient == null || patient.getRegisteredAt() == null) return 0.0;
        // Earlier registration gets higher score
        long millisSinceEpoch = patient.getRegisteredAt().toEpochMilli();
        return 1_000_000_000_000.0 - millisSinceEpoch;
    }

    @Override
    public boolean isFairnessBoosted(Patient patient, Instant now, Settings settings) {
        return false;
    }

    @Override
    public String explain(Patient patient, Instant now, Settings settings) {
        long waited = Math.max(0, Duration.between(patient.getRegisteredAt(), now).toMinutes());
        return "First come first served priority with " + waited + " mins waiting time.";
    }
}
