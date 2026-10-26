package com.smartopd.scoring;

import com.smartopd.config.Settings;
import com.smartopd.model.Patient;
import java.time.Instant;

/**
 * PriorityStrategy - Strategy pattern interface defining the algorithmic contract
 * for ranking patients within an OPD queue.
 * 
 * ============================================================================
 * OOP Concept: Strategy Design Pattern (Behavioral) & Polymorphism
 * - Enables swapping queue ordering algorithms (SmartOPD, Strict Priority, FCFS)
 *   at runtime without altering the DoctorQueue or QueueManager code.
 * ============================================================================
 */
public interface PriorityStrategy {

    /**
     * Calculates the dynamic numerical priority score for a patient.
     * 
     * @param patient patient being scored
     * @param now current instant
     * @param settings hospital scoring parameters
     * @return numerical priority score (higher score = earlier position)
     */
    double calculateScore(Patient patient, Instant now, Settings settings);

    /**
     * Determines whether the patient has crossed the anti-starvation threshold
     * and is actively receiving the fairness boost bonus.
     * 
     * @param patient patient being checked
     * @param now current instant
     * @param settings hospital scoring parameters
     * @return true if fairness boost is currently applied
     */
    boolean isFairnessBoosted(Patient patient, Instant now, Settings settings);

    /**
     * Generates a plain-English explainability sentence detailing why the patient
     * has been allocated their current priority in the queue.
     * 
     * @param patient patient being explained
     * @param now current instant
     * @param settings hospital scoring parameters
     * @return explainability string
     */
    String explain(Patient patient, Instant now, Settings settings);
}
