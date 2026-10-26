package com.smartopd.scoring;

import com.smartopd.config.Settings;
import com.smartopd.model.Patient;
import java.time.Duration;
import java.time.Instant;

/**
 * StrictPriorityStrategy - Baseline policy prioritizing solely by clinical triage severity.
 * 
 * ============================================================================
 * OOP Concept: Strategy Pattern Implementation
 * - Ignores waiting time anti-starvation boost (illustrates the starvation problem).
 * ============================================================================
 */
public class StrictPriorityStrategy implements PriorityStrategy {

    @Override
    public double calculateScore(Patient patient, Instant now, Settings settings) {
        if (patient == null) return 0.0;
        long waited = Math.max(0, Duration.between(patient.getRegisteredAt(), now).toMinutes());
        // Higher severity gets top priority; micro-tiebreak by waiting
        return (patient.getSeverity() * 1000.0) + (waited * 0.01);
    }

    @Override
    public boolean isFairnessBoosted(Patient patient, Instant now, Settings settings) {
        return false; // Strict priority never awards fairness boost
    }

    @Override
    public String explain(Patient patient, Instant now, Settings settings) {
        return "Strict priority: Severity Level " + patient.getSeverity() + " clinical triage.";
    }
}
