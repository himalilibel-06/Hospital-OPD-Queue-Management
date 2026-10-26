package com.smartopd.simulation;

import java.util.List;

/**
 * PolicySimulationResult - Result object for a single policy simulation run.
 */
public record PolicySimulationResult(
        String policyName,
        int totalServed,
        int totalSimTime,
        PolicyMetrics metrics,
        List<PatientWaitRecord> patientWaits,
        List<PatientWaitRecord> records,
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

    public static PolicySimulationResult of(
            String policyName,
            int totalServed,
            int totalSimTime,
            PolicyMetrics metrics,
            List<PatientWaitRecord> patientWaits
    ) {
        return new PolicySimulationResult(
                policyName,
                totalServed,
                totalSimTime,
                metrics,
                patientWaits,
                patientWaits,
                metrics.averageWaitOverall(),
                metrics.avgWaitOverall(),
                metrics.averageWaitEmergency(),
                metrics.avgWaitEmergency(),
                metrics.averageWaitHighSeverity(),
                metrics.avgWaitHighSeverity(),
                metrics.averageWaitScheduled(),
                metrics.avgWaitScheduled(),
                metrics.averageWaitWalkin(),
                metrics.avgWaitWalkin(),
                metrics.p90Wait(),
                metrics.maxScheduledWait(),
                metrics.maxWaitScheduled(),
                metrics.starvationCount(),
                metrics.doctorIdlePercent()
        );
    }
}
