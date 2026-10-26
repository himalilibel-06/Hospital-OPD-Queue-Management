package com.smartopd.simulation;

/**
 * SyntheticPatientDto - Lightweight view of generated patient parameters before simulation.
 */
public record SyntheticPatientDto(
        String id,
        int index,
        String category,
        int severity,
        int arrivalMinute,
        Integer appointmentMinute,
        int consultDuration
) {}
