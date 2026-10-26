package com.smartopd.api.controller;

import com.smartopd.api.dto.SimulationPresetDto;
import com.smartopd.api.dto.SimulationRunRequest;
import com.smartopd.api.security.RequiresRole;
import com.smartopd.config.Settings;
import com.smartopd.exception.ValidationException;
import com.smartopd.service.QueueService;
import com.smartopd.simulation.SimulationConfig;
import com.smartopd.simulation.SimulationResult;
import com.smartopd.simulation.Simulator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.*;

/**
 * SimulationController - REST API for hospital discrete-event queue simulations.
 * 
 * ============================================================================
 * Security & Access Rules:
 * - Restricted to Front Desk (RECEPTIONIST) role only.
 * - Enforces Bean Validation boundaries (e.g. 50 <= numPatients <= 500).
 * - Enforces a strict 10-second processing limit to protect server availability.
 * - Injects the live hospital Settings from the SQLite database.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/simulation")
@RequiresRole("RECEPTIONIST")
public class SimulationController {

    private final QueueService queueService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public SimulationController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * Lists the three standard hospital simulation presets with pre-calculated offered load.
     */
    @GetMapping("/presets")
    public ResponseEntity<List<SimulationPresetDto>> getPresets() {
        SimulationConfig quiet = SimulationConfig.quietDay(null);
        SimulationConfig busy = SimulationConfig.busyDay(null);
        SimulationConfig surge = SimulationConfig.emergencySurge(null);

        List<SimulationPresetDto> presets = List.of(
                new SimulationPresetDto(
                        "quiet-day",
                        "Quiet Day",
                        "Low patient volume, high scheduled appointments ratio, minimal emergencies. (~60% load)",
                        quiet.numPatients(),
                        quiet.numDoctors(),
                        quiet.avgConsultMinutes(),
                        quiet.emergencyPercent(),
                        quiet.scheduledPercent(),
                        quiet.arrivalSpreadMinutes(),
                        quiet.randomSeed(),
                        quiet.offeredLoadPercent()
                ),
                new SimulationPresetDto(
                        "busy-day",
                        "Busy Day",
                        "Typical heavy hospital weekday with regular emergencies and steady walk-ins. (~90% load)",
                        busy.numPatients(),
                        busy.numDoctors(),
                        busy.avgConsultMinutes(),
                        busy.emergencyPercent(),
                        busy.scheduledPercent(),
                        busy.arrivalSpreadMinutes(),
                        busy.randomSeed(),
                        busy.offeredLoadPercent()
                ),
                new SimulationPresetDto(
                        "emergency-surge",
                        "Emergency Surge",
                        "Mass casualty / monsoon surge with high emergency intake stressing routine OPD. (~105% load)",
                        surge.numPatients(),
                        surge.numDoctors(),
                        surge.avgConsultMinutes(),
                        surge.emergencyPercent(),
                        surge.scheduledPercent(),
                        surge.arrivalSpreadMinutes(),
                        surge.randomSeed(),
                        surge.offeredLoadPercent()
                )
        );

        return ResponseEntity.ok(presets);
    }

    /**
     * Executes discrete-event simulation comparing FCFS, Strict Priority, and SmartOPD.
     * Uses the live algorithm Settings from the database and returns complete policy metrics,
     * patient timeline traces, and plain-English comparative insights.
     */
    @PostMapping("/run")
    public ResponseEntity<SimulationResult> runSimulation(@Valid @RequestBody(required = false) SimulationRunRequest request) {
        SimulationConfig baseConfig;
        if (request != null && request.preset() != null && !request.preset().isBlank()) {
            baseConfig = SimulationConfig.fromPreset(request.preset(), request.randomSeed());
        } else {
            baseConfig = SimulationConfig.busyDay(request != null ? request.randomSeed() : null);
        }

        SimulationConfig finalConfig;
        if (request != null) {
            int np = request.numPatients() != null ? request.numPatients() : baseConfig.numPatients();
            int nd = request.numDoctors() != null ? request.numDoctors() : baseConfig.numDoctors();
            int ac = request.avgConsultMinutes() != null ? request.avgConsultMinutes() : baseConfig.avgConsultMinutes();
            int ep = request.emergencyPercent() != null ? request.emergencyPercent() : baseConfig.emergencyPercent();
            int sp = request.scheduledPercent() != null ? request.scheduledPercent() : baseConfig.scheduledPercent();
            int as = request.arrivalSpreadMinutes() != null ? request.arrivalSpreadMinutes() : baseConfig.arrivalSpreadMinutes();
            long seed = request.randomSeed() != null ? request.randomSeed() : baseConfig.randomSeed();
            finalConfig = new SimulationConfig(np, nd, ac, ep, sp, as, seed);
        } else {
            finalConfig = baseConfig;
        }

        Settings liveSettings = queueService.getSettings();

        // Enforce 10-second processing limit
        Future<SimulationResult> future = executorService.submit(() -> Simulator.runSimulation(finalConfig, liveSettings));
        try {
            SimulationResult result = future.get(10, TimeUnit.SECONDS);
            return ResponseEntity.ok(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Simulation execution exceeded 10-second time limit.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ValidationException ve) {
                throw ve;
            }
            throw new RuntimeException("Simulation execution failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation interrupted.", e);
        }
    }
}
