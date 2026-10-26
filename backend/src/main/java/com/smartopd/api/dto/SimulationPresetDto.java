package com.smartopd.api.dto;

/**
 * SimulationPresetDto - Specification of a hospital load preset for discrete-event queue simulations.
 */
public record SimulationPresetDto(
        String id,
        String name,
        String description,
        int numPatients,
        int numDoctors,
        int avgConsultMinutes,
        int emergencyPercent,
        int scheduledPercent,
        int arrivalSpreadMinutes,
        long randomSeed,
        int offeredLoadPercent
) {}
