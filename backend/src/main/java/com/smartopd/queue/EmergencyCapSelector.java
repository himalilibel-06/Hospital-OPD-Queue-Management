package com.smartopd.queue;

import com.smartopd.config.Settings;
import com.smartopd.model.Category;
import com.smartopd.model.Patient;
import com.smartopd.scoring.PriorityStrategy;
import java.time.Instant;
import java.util.List;

/**
 * EmergencyCapSelector - Reusable anti-starvation emergency cap selection logic.
 * 
 * ============================================================================
 * OOP Concept: Single Responsibility Principle (SRP) & Algorithmic Reuse
 * - Encapsulates the triage fairness invariant across both live clinical queues
 *   (DoctorQueue) and discrete-event simulation runs (Simulator).
 * - Rule: If a doctor has served MAX_CONSECUTIVE_EMERGENCIES in a row and the
 *   next candidate is not Level 5 (immediate life threat), the queue temporarily
 *   yields to the longest-waiting fairness-boosted patient.
 * ============================================================================
 */
public class EmergencyCapSelector {

    /**
     * Immutable outcome of evaluating the emergency cap policy.
     * 
     * @param selectedPatient the patient chosen to be consulted next
     * @param fairnessCapTriggered true if an emergency was postponed to protect a starving patient
     */
    public record SelectionResult(Patient selectedPatient, boolean fairnessCapTriggered) {}

    /**
     * Selects the next patient from an already score-ranked queue, enforcing the emergency cap.
     * 
     * @param queue list of waiting patients ordered by PriorityStrategy descending
     * @param consecutiveEmergencies number of emergencies already served in succession by the doctor
     * @param maxConsecutive maximum consecutive emergencies permitted before intervention
     * @param strategy active priority strategy used to check fairness boost eligibility
     * @param now current simulation or system instant
     * @param settings hospital scoring parameters
     * @return SelectionResult containing the selected patient and whether cap was triggered
     */
    public static SelectionResult selectNextPatient(
            List<Patient> queue,
            int consecutiveEmergencies,
            int maxConsecutive,
            PriorityStrategy strategy,
            Instant now,
            Settings settings
    ) {
        if (queue == null || queue.isEmpty()) {
            return new SelectionResult(null, false);
        }

        Patient selected = queue.get(0);
        boolean fairnessCapTriggered = false;

        // Emergency Cap Rule:
        // If consecutive emergencies reached threshold, and top candidate is not Severity 5,
        // scan queue in order to pick the longest-waiting fairness-boosted patient.
        if (consecutiveEmergencies >= maxConsecutive) {
            if (selected.getSeverity() < 5 && (selected.getSeverity() == 4 || selected.getCategory() == Category.EMERGENCY)) {
                for (Patient p : queue) {
                    if (strategy.isFairnessBoosted(p, now, settings)) {
                        selected = p;
                        fairnessCapTriggered = true;
                        break;
                    }
                }
            }
        }

        return new SelectionResult(selected, fairnessCapTriggered);
    }
}
