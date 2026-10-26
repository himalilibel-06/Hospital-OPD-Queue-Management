package com.smartopd.simulation;

/**
 * PolicyMetrics - Statistical performance measurements produced by a simulation policy run.
 * 
 * ============================================================================
 * Metrics Glossary & Viva Explanation:
 * 1. averageWaitOverall: Arithmetic mean waiting time (in minutes) across all served patients.
 * 2. averageWaitEmergency: Mean wait for Emergency category patients (Triage Levels 4 & 5).
 * 3. averageWaitHighSeverity: Mean wait for patients with clinical severity >= 4.
 * 4. averageWaitScheduled: Mean wait for pre-booked appointment patients.
 * 5. averageWaitWalkin: Mean wait for unregistered walk-in lobby arrivals.
 * 6. maxScheduledWait: Worst-case delay experienced by any scheduled patient.
 * 7. starvationCount: Number of scheduled patients whose wait exceeded 2x the fairness threshold (e.g. >90 mins).
 * 8. p90Wait: 90th percentile wait time (90% of all patients waited <= this value).
 * 9. doctorIdlePercent: Percentage of total doctor shift capacity spent waiting idle without patients.
 * ============================================================================
 */
public record PolicyMetrics(
        int averageWaitOverall,
        int avgWaitOverall,
        int averageWaitEmergency,
        int avgWaitEmergency,
        int averageWaitHighSeverity,
        int avgWaitHighSeverity,
        int averageWaitScheduled,
        int avgWaitScheduled,
        int averageWaitWalkin,
        int avgWaitWalkin,
        int p90Wait,
        int maxScheduledWait,
        int maxWaitScheduled,
        int starvationCount,
        int doctorIdlePercent
) {

    public static PolicyMetrics of(
            int averageWaitOverall,
            int averageWaitEmergency,
            int averageWaitHighSeverity,
            int averageWaitScheduled,
            int averageWaitWalkin,
            int p90Wait,
            int maxScheduledWait,
            int starvationCount,
            int doctorIdlePercent
    ) {
        return new PolicyMetrics(
                averageWaitOverall,
                averageWaitOverall,
                averageWaitEmergency,
                averageWaitEmergency,
                averageWaitHighSeverity,
                averageWaitHighSeverity,
                averageWaitScheduled,
                averageWaitScheduled,
                averageWaitWalkin,
                averageWaitWalkin,
                p90Wait,
                maxScheduledWait,
                maxScheduledWait,
                starvationCount,
                doctorIdlePercent
        );
    }
}
