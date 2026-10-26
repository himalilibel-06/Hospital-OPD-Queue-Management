package com.smartopd.simulation;

import com.smartopd.exception.ValidationException;

/**
 * SimulationConfig - Immutable parameter configuration for discrete-event OPD simulation runs.
 * 
 * ============================================================================
 * OOP Concept: Record Pattern, Immutability & Defensive Precondition Validation
 * - Validates patient volume (50..500), doctor capacity (1..6), arrival spread,
 *   clinical mix percentages, and consultation duration.
 * - Provides static factory methods for canonical hospital stress presets
 *   (Quiet Day, Busy Day, Emergency Surge).
 * ============================================================================
 */
public record SimulationConfig(
        int numPatients,
        int numDoctors,
        int avgConsultMinutes,
        int emergencyPercent,
        int scheduledPercent,
        int arrivalSpreadMinutes,
        long randomSeed
) {

    public SimulationConfig {
        if (numPatients < 50 || numPatients > 500) {
            throw new ValidationException("Number of patients must be between 50 and 500 (received: " + numPatients + ").");
        }
        if (numDoctors < 1 || numDoctors > 6) {
            throw new ValidationException("Number of doctors must be between 1 and 6 (received: " + numDoctors + ").");
        }
        if (avgConsultMinutes < 3 || avgConsultMinutes > 60) {
            throw new ValidationException("Average consultation duration must be between 3 and 60 minutes (received: " + avgConsultMinutes + ").");
        }
        if (emergencyPercent < 0 || emergencyPercent > 100) {
            throw new ValidationException("Emergency percentage must be between 0 and 100 (received: " + emergencyPercent + ").");
        }
        if (scheduledPercent < 0 || scheduledPercent > 100) {
            throw new ValidationException("Scheduled percentage must be between 0 and 100 (received: " + scheduledPercent + ").");
        }
        if (emergencyPercent + scheduledPercent > 100) {
            throw new ValidationException("Combined emergency and scheduled percentages cannot exceed 100% (received: "
                    + (emergencyPercent + scheduledPercent) + "%).");
        }
        if (arrivalSpreadMinutes < 60 || arrivalSpreadMinutes > 1440) {
            throw new ValidationException("Arrival spread must be between 60 and 1440 minutes (received: " + arrivalSpreadMinutes + ").");
        }
    }

    /**
     * Computes the theoretical offered load percentage on the simulated OPD facility:
     * Offered Load = (Total Work Needed) / (Total Doctor Capacity)
     * Load % = round(((numPatients * avgConsultMinutes) / (numDoctors * arrivalSpreadMinutes)) * 100)
     * 
     * @return integer load percentage (e.g. 60 for Quiet Day, 90 for Busy Day, 105 for Emergency Surge)
     */
    public int offeredLoadPercent() {
        double workNeeded = (double) numPatients * (double) avgConsultMinutes;
        double capacity = (double) numDoctors * (double) arrivalSpreadMinutes;
        return (int) Math.round((workNeeded / capacity) * 100.0);
    }

    // --- Static Preset Factories ---

    /**
     * Quiet Day Preset:
     * Low patient volume, high scheduled appointments ratio, minimal emergencies (~60% offered load).
     */
    public static SimulationConfig quietDay(Long seedOverride) {
        return new SimulationConfig(
                90,
                4,
                8,
                5,
                65,
                300,
                seedOverride != null ? seedOverride : 101L
        );
    }

    /**
     * Busy Day Preset:
     * Typical heavy hospital weekday with regular emergencies and steady walk-ins (~90% offered load).
     */
    public static SimulationConfig busyDay(Long seedOverride) {
        return new SimulationConfig(
                108,
                4,
                10,
                12,
                48,
                300,
                seedOverride != null ? seedOverride : 202L
        );
    }

    /**
     * Emergency Surge Preset:
     * Mass casualty / monsoon trauma surge stressing routine OPD capacity (~105% offered load).
     */
    public static SimulationConfig emergencySurge(Long seedOverride) {
        return new SimulationConfig(
                126,
                4,
                10,
                28,
                35,
                300,
                seedOverride != null ? seedOverride : 303L
        );
    }

    /**
     * Resolves a SimulationConfig from a preset identifier, applying optional seed overrides.
     * 
     * @param presetName canonical preset identifier (quiet-day, busy-day, emergency-surge)
     * @param seedOverride optional pseudo-random seed override
     * @return validated SimulationConfig
     */
    public static SimulationConfig fromPreset(String presetName, Long seedOverride) {
        if (presetName == null || presetName.isBlank()) {
            return busyDay(seedOverride);
        }
        String normalized = presetName.trim().toLowerCase().replace("_", "-");
        return switch (normalized) {
            case "quiet-day", "quiet" -> quietDay(seedOverride);
            case "busy-day", "busy" -> busyDay(seedOverride);
            case "emergency-surge", "surge", "emergency" -> emergencySurge(seedOverride);
            default -> throw new ValidationException("Unknown simulation preset: '" + presetName
                    + "'. Valid presets are 'quiet-day', 'busy-day', and 'emergency-surge'.");
        };
    }
}
