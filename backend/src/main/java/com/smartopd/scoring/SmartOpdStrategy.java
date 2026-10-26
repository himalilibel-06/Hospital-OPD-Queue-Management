package com.smartopd.scoring;

import com.smartopd.config.Settings;
import com.smartopd.model.Category;
import com.smartopd.model.Patient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * SmartOpdStrategy - Default fairness-aware triage policy.
 * 
 * ============================================================================
 * OOP Concept: Strategy Pattern Implementation & Algorithmic Encapsulation
 * Combines:
 * 1. Level 5 Critical Emergency Override (10,000 pts)
 * 2. Base Clinical Severity (Severity * SeverityWeight)
 * 3. Waiting Time Aging Factor (WaitMinutes * WaitWeightPerMin)
 * 4. Scheduled Appointment Proximity Urgency (+60 pts max)
 * 5. Anti-Starvation Fairness Boost (+150 pts after 45m threshold)
 * 6. Temporary Skip Penalty (-300 pts)
 * ============================================================================
 */
public class SmartOpdStrategy implements PriorityStrategy {

    @Override
    public double calculateScore(Patient patient, Instant now, Settings settings) {
        if (patient == null) return 0.0;
        Settings s = settings != null ? settings : new Settings();
        Instant current = now != null ? now : Instant.now();
        long waitedMinutes = getWaitedMinutes(patient, current);

        // 1. Level 5 Emergency Override (10,000 pts)
        int overrideSev = s.getEmergencyOverrideSeverity();
        double overrideScore = s.getEmergencyOverrideScore();
        if (patient.getSeverity() == overrideSev ||
            (patient.getCategory() == Category.EMERGENCY && patient.getSeverity() >= 5)) {
            return overrideScore + (waitedMinutes * 0.1);
        }

        // 2. Base Clinical Severity
        double score = patient.getSeverity() * s.getSeverityWeight();

        // 3. Waiting Time Aging Factor
        score += waitedMinutes * s.getWaitWeightPerMin();

        // 4. Appointment Urgency (polymorphic dynamic dispatch)
        score += patient.urgencyBonus(current);

        // 5. Anti-Starvation Fairness Boost (polymorphic dynamic dispatch)
        int threshold = patient.fairnessThresholdMinutes(s);
        if (waitedMinutes >= threshold) {
            score += s.getFairnessBoost();
        }

        // 6. Skip penalty (if temporarily skipped)
        if (patient.isSkipped()) {
            score -= 300.0;
        }

        return Math.round(score);
    }

    @Override
    public boolean isFairnessBoosted(Patient patient, Instant now, Settings settings) {
        if (patient == null || patient.getSeverity() >= 5) {
            return false;
        }
        Settings s = settings != null ? settings : new Settings();
        Instant current = now != null ? now : Instant.now();
        long waitedMinutes = getWaitedMinutes(patient, current);

        int threshold = patient.fairnessThresholdMinutes(s);
        return waitedMinutes >= threshold;
    }

    @Override
    public String explain(Patient patient, Instant now, Settings settings) {
        if (patient == null) return "No patient data.";
        Settings s = settings != null ? settings : new Settings();
        Instant current = now != null ? now : Instant.now();
        long waitedMinutes = getWaitedMinutes(patient, current);

        if (patient.getSeverity() == 5) {
            return "Severity 5 emergency override: critical case escalated to top of queue ahead of routine visits.";
        }

        if (patient.getSeverity() == 4) {
            return "Urgent triage priority (Severity Level 4) with " + waitedMinutes + " mins waiting time.";
        }

        int threshold = patient.fairnessThresholdMinutes(s);

        if (waitedMinutes >= threshold) {
            return "Fairness boost applied (+" + (int) s.getFairnessBoost() + " pts): waiting time ("
                    + waitedMinutes + " min) exceeded " + threshold + "-min threshold.";
        }

        if (patient.getCategory() == Category.SCHEDULED && patient.getAppointmentTime() != null) {
            return "Scheduled slot (" + patient.getAppointmentTime() + ") with Level "
                    + patient.getSeverity() + " severity and " + waitedMinutes + " mins waiting time.";
        }

        return "Standard queue priority: Severity Level " + patient.getSeverity()
                + " with " + waitedMinutes + " mins in queue.";
    }

    public long getWaitedMinutes(Patient patient, Instant now) {
        if (patient == null || patient.getRegisteredAt() == null || now == null) {
            return 0;
        }
        long diff = Duration.between(patient.getRegisteredAt(), now).toMinutes();
        return Math.max(0, diff);
    }

    private double calculateAppointmentUrgency(Patient patient, Instant now, double urgencyMax) {
        String apptTime = patient.getAppointmentTime();
        if (apptTime == null || !apptTime.contains(":")) {
            return 0.0;
        }
        try {
            String[] parts = apptTime.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int min = Integer.parseInt(parts[1].trim());

            ZonedDateTime nowZoned = now.atZone(ZoneId.systemDefault());
            ZonedDateTime apptZoned = nowZoned.withHour(hour).withMinute(min).withSecond(0).withNano(0);
            long diffMinutes = Duration.between(nowZoned, apptZoned).toMinutes();

            if (diffMinutes <= 0) {
                return urgencyMax;
            } else if (diffMinutes <= 30) {
                return Math.max(0, 30 - diffMinutes) * 1.5;
            }
        } catch (Exception ignored) {
        }
        return 0.0;
    }
}
