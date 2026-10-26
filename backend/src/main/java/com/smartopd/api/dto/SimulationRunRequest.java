package com.smartopd.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * SimulationRunRequest - Inbound payload requesting a discrete-event simulation run.
 * Supports running a named preset or custom parameter sweeps with Bean Validation bounds.
 */
public record SimulationRunRequest(
        String preset,

        @Min(value = 50, message = "numPatients must be between 50 and 500")
        @Max(value = 500, message = "numPatients must be between 50 and 500")
        Integer numPatients,

        @Min(value = 1, message = "numDoctors must be between 1 and 6")
        @Max(value = 6, message = "numDoctors must be between 1 and 6")
        Integer numDoctors,

        @Min(value = 3, message = "avgConsultMinutes must be between 3 and 60 minutes")
        @Max(value = 60, message = "avgConsultMinutes must be between 3 and 60 minutes")
        Integer avgConsultMinutes,

        @Min(value = 0, message = "emergencyPercent must be between 0 and 100")
        @Max(value = 100, message = "emergencyPercent must be between 0 and 100")
        Integer emergencyPercent,

        @Min(value = 0, message = "scheduledPercent must be between 0 and 100")
        @Max(value = 100, message = "scheduledPercent must be between 0 and 100")
        Integer scheduledPercent,

        @Min(value = 60, message = "arrivalSpreadMinutes must be between 60 and 1440")
        @Max(value = 1440, message = "arrivalSpreadMinutes must be between 60 and 1440")
        Integer arrivalSpreadMinutes,

        Long randomSeed
) {}
