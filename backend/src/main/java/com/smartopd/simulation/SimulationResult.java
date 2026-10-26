package com.smartopd.simulation;

import java.util.List;
import java.util.Map;

/**
 * SimulationResult - Comprehensive outcome of running a 3-policy discrete-event simulation run.
 */
public record SimulationResult(
        SimulationConfig config,
        int offeredLoadPercent,
        int patientsCount,
        List<SyntheticPatientDto> patientList,
        Map<String, PolicySimulationResult> policies,
        Map<String, PolicySimulationResult> results,
        List<String> insights
) {}
