package com.smartopd.simulation;

/**
 * PatientWaitRecord - Granular consultation timeline record for individual simulated patients.
 * Serialized to JSON to feed frontend analytics charts, scatter plots, and CSV export.
 */
public record PatientWaitRecord(
        String id,
        String patientId,
        String tokenNumber,
        String category,
        int severity,
        int arrivalMinute,
        int arrivalTime,
        int startMinute,
        int startTime,
        int finishMinute,
        int completionTime,
        int waitMinutes,
        boolean starved
) {

    public static PatientWaitRecord of(
            String id,
            String category,
            int severity,
            int arrivalMinute,
            int startMinute,
            int finishMinute,
            int waitMinutes,
            boolean starved
    ) {
        return new PatientWaitRecord(
                id,
                id,
                id,
                category,
                severity,
                arrivalMinute,
                arrivalMinute,
                startMinute,
                startMinute,
                finishMinute,
                finishMinute,
                waitMinutes,
                starved
        );
    }
}
