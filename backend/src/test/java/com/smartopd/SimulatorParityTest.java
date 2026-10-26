package com.smartopd;

import com.smartopd.config.Settings;
import com.smartopd.exception.ValidationException;
import com.smartopd.simulation.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimulatorParityTest - Verifies 100% parity between the Java Simulator engine
 * and the JavaScript executable specification (frontend/js/simulator.js).
 */
public class SimulatorParityTest {

    record ExpectedMetrics(
            int avgWaitOverall,
            int avgWaitEmergency,
            int avgWaitHighSeverity,
            int avgWaitScheduled,
            int avgWaitWalkin,
            int maxScheduledWait,
            int starvationCount,
            int p90Wait,
            int doctorIdlePercent
    ) {}

    record GoldenTestCase(
            String preset,
            long seed,
            int expectedOfferedLoad,
            ExpectedMetrics fcfs,
            ExpectedMetrics strict,
            ExpectedMetrics smart
    ) {}

    static Stream<GoldenTestCase> goldenCasesProvider() {
        return Stream.of(
                // 1. Quiet Day, Seed 1
                new GoldenTestCase(
                        "quiet-day", 1L, 60,
                        new ExpectedMetrics(1, 0, 0, 1, 1, 7, 0, 3, 43),
                        new ExpectedMetrics(1, 0, 0, 1, 1, 14, 0, 2, 43),
                        new ExpectedMetrics(1, 0, 0, 1, 1, 14, 0, 2, 43)
                ),
                // 2. Quiet Day, Seed 7
                new GoldenTestCase(
                        "quiet-day", 7L, 60,
                        new ExpectedMetrics(1, 1, 1, 1, 0, 8, 0, 4, 40),
                        new ExpectedMetrics(1, 0, 0, 1, 1, 10, 0, 4, 40),
                        new ExpectedMetrics(1, 0, 0, 1, 1, 6, 0, 4, 40)
                ),
                // 3. Quiet Day, Seed 42
                new GoldenTestCase(
                        "quiet-day", 42L, 60,
                        new ExpectedMetrics(1, 1, 0, 0, 1, 5, 0, 2, 42),
                        new ExpectedMetrics(1, 1, 0, 0, 1, 8, 0, 2, 42),
                        new ExpectedMetrics(1, 0, 0, 0, 1, 4, 0, 1, 42)
                ),
                // 4. Busy Day, Seed 1
                new GoldenTestCase(
                        "busy-day", 1L, 90,
                        new ExpectedMetrics(3, 2, 2, 3, 3, 13, 0, 8, 13),
                        new ExpectedMetrics(3, 1, 1, 4, 3, 23, 0, 9, 13),
                        new ExpectedMetrics(3, 1, 1, 3, 4, 21, 0, 10, 13)
                ),
                // 5. Busy Day, Seed 7
                new GoldenTestCase(
                        "busy-day", 7L, 90,
                        new ExpectedMetrics(7, 4, 6, 7, 7, 24, 0, 20, 12),
                        new ExpectedMetrics(7, 1, 1, 5, 11, 53, 0, 34, 12),
                        new ExpectedMetrics(7, 1, 1, 5, 11, 30, 0, 29, 12)
                ),
                // 6. Busy Day, Seed 42
                new GoldenTestCase(
                        "busy-day", 42L, 90,
                        new ExpectedMetrics(10, 8, 9, 11, 10, 29, 0, 24, 14),
                        new ExpectedMetrics(10, 1, 1, 10, 12, 62, 0, 42, 14),
                        new ExpectedMetrics(10, 1, 1, 8, 15, 41, 0, 39, 14)
                ),
                // 7. Emergency Surge, Seed 1
                new GoldenTestCase(
                        "emergency-surge", 1L, 105,
                        new ExpectedMetrics(10, 10, 10, 9, 11, 23, 0, 20, 3),
                        new ExpectedMetrics(10, 2, 2, 15, 14, 48, 0, 39, 3),
                        new ExpectedMetrics(10, 2, 2, 10, 18, 35, 0, 37, 3)
                ),
                // 8. Emergency Surge, Seed 7
                new GoldenTestCase(
                        "emergency-surge", 7L, 105,
                        new ExpectedMetrics(13, 13, 13, 12, 15, 42, 0, 38, 10),
                        new ExpectedMetrics(14, 1, 1, 13, 22, 115, 2, 40, 10),
                        new ExpectedMetrics(13, 1, 1, 8, 23, 51, 0, 46, 10)
                ),
                // 9. Emergency Surge, Seed 42
                new GoldenTestCase(
                        "emergency-surge", 42L, 105,
                        new ExpectedMetrics(11, 10, 10, 12, 11, 28, 0, 25, 4),
                        new ExpectedMetrics(11, 1, 2, 11, 18, 86, 0, 25, 3),
                        new ExpectedMetrics(11, 2, 2, 12, 18, 45, 0, 40, 4)
                )
        );
    }

    @ParameterizedTest(name = "{0} (Seed {1}) - Golden parity verification")
    @MethodSource("goldenCasesProvider")
    @DisplayName("Parity: 9 Golden Preset Runs Match JavaScript Simulator Exactly")
    void testGoldenParity(GoldenTestCase testCase) {
        SimulationConfig config = SimulationConfig.fromPreset(testCase.preset(), testCase.seed());
        Settings settings = new Settings(); // default 100, 3, 150, 45, 45, cap 2
        SimulationResult result = Simulator.runSimulation(config, settings);

        assertEquals(testCase.expectedOfferedLoad(), result.offeredLoadPercent(),
                "Offered load mismatch for preset " + testCase.preset());

        assertMetricsMatch("FCFS", testCase.fcfs(), result.policies().get("fcfs").metrics());
        assertMetricsMatch("StrictPriority", testCase.strict(), result.policies().get("strictPriority").metrics());
        assertMetricsMatch("SmartOPD", testCase.smart(), result.policies().get("smartopd").metrics());
    }

    private void assertMetricsMatch(String policyName, ExpectedMetrics expected, PolicyMetrics actual) {
        assertEquals(expected.avgWaitOverall(), actual.avgWaitOverall(), policyName + ": avgWaitOverall mismatch");
        assertEquals(expected.avgWaitEmergency(), actual.avgWaitEmergency(), policyName + ": avgWaitEmergency mismatch");
        assertEquals(expected.avgWaitHighSeverity(), actual.avgWaitHighSeverity(), policyName + ": avgWaitHighSeverity mismatch");
        assertEquals(expected.avgWaitScheduled(), actual.avgWaitScheduled(), policyName + ": avgWaitScheduled mismatch");
        assertEquals(expected.avgWaitWalkin(), actual.avgWaitWalkin(), policyName + ": avgWaitWalkin mismatch");
        assertEquals(expected.maxScheduledWait(), actual.maxScheduledWait(), policyName + ": maxScheduledWait mismatch");
        assertEquals(expected.starvationCount(), actual.starvationCount(), policyName + ": starvationCount mismatch");
        assertEquals(expected.p90Wait(), actual.p90Wait(), policyName + ": p90Wait mismatch");
        assertEquals(expected.doctorIdlePercent(), actual.doctorIdlePercent(), policyName + ": doctorIdlePercent mismatch");
    }

    @Test
    @DisplayName("Determinism: Identical seed produces identical results")
    void testDeterminism() {
        SimulationConfig config = SimulationConfig.busyDay(12345L);
        Settings settings = new Settings();

        SimulationResult runA = Simulator.runSimulation(config, settings);
        SimulationResult runB = Simulator.runSimulation(config, settings);

        assertEquals(runA.offeredLoadPercent(), runB.offeredLoadPercent());
        assertEquals(runA.policies().get("smartopd").metrics(), runB.policies().get("smartopd").metrics());

        List<PatientWaitRecord> waitsA = runA.policies().get("smartopd").patientWaits();
        List<PatientWaitRecord> waitsB = runB.policies().get("smartopd").patientWaits();
        assertEquals(waitsA.size(), waitsB.size());
        for (int i = 0; i < waitsA.size(); i++) {
            assertEquals(waitsA.get(i), waitsB.get(i), "Patient wait record differed at index " + i);
        }
    }

    @Test
    @DisplayName("Completeness: All three policies serve every patient exactly once")
    void testAllServedOnce() {
        SimulationConfig config = new SimulationConfig(75, 3, 10, 15, 45, 300, 888L);
        SimulationResult result = Simulator.runSimulation(config, new Settings());

        for (String polKey : List.of("fcfs", "strictPriority", "smartopd")) {
            PolicySimulationResult polRes = result.policies().get(polKey);
            assertEquals(75, polRes.totalServed(), polKey + " totalServed mismatch");
            assertEquals(75, polRes.patientWaits().size(), polKey + " patientWaits size mismatch");

            Set<String> uniqueIds = new HashSet<>();
            for (PatientWaitRecord record : polRes.patientWaits()) {
                uniqueIds.add(record.id());
            }
            assertEquals(75, uniqueIds.size(), polKey + " did not serve all 75 unique patients");
        }
    }

    @Test
    @DisplayName("Performance: 500 patient simulation finishes in under 2 seconds")
    void testPerformance500Patients() {
        SimulationConfig config = new SimulationConfig(500, 4, 10, 15, 45, 300, 42L);
        long start = System.currentTimeMillis();
        SimulationResult result = Simulator.runSimulation(config, new Settings());
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(500, result.patientsCount());
        assertTrue(elapsed < 2000, "500-patient simulation took " + elapsed + "ms (limit: 2000ms)");
    }

    @Test
    @DisplayName("Validation: Invalid configurations throw ValidationException with readable messages")
    void testValidation() {
        // Patients below 50
        ValidationException ex1 = assertThrows(ValidationException.class,
                () -> new SimulationConfig(40, 4, 10, 15, 45, 300, 42L));
        assertTrue(ex1.getMessage().contains("50 and 500"));

        // Patients above 500
        ValidationException ex2 = assertThrows(ValidationException.class,
                () -> new SimulationConfig(501, 4, 10, 15, 45, 300, 42L));
        assertTrue(ex2.getMessage().contains("50 and 500"));

        // Doctors < 1
        ValidationException ex3 = assertThrows(ValidationException.class,
                () -> new SimulationConfig(100, 0, 10, 15, 45, 300, 42L));
        assertTrue(ex3.getMessage().contains("1 and 6"));

        // Doctors > 6
        ValidationException ex4 = assertThrows(ValidationException.class,
                () -> new SimulationConfig(100, 7, 10, 15, 45, 300, 42L));
        assertTrue(ex4.getMessage().contains("1 and 6"));

        // Combined percentages > 100
        ValidationException ex5 = assertThrows(ValidationException.class,
                () -> new SimulationConfig(100, 4, 10, 60, 50, 300, 42L));
        assertTrue(ex5.getMessage().contains("cannot exceed 100%"));

        // Unknown preset
        ValidationException ex6 = assertThrows(ValidationException.class,
                () -> SimulationConfig.fromPreset("unknown-preset", null));
        assertTrue(ex6.getMessage().contains("Unknown simulation preset"));
    }

    @Test
    @DisplayName("Presets: Offered load ranges computed from actual preset objects (50-70, 80-100, 100-115)")
    void testPresetOfferedLoadRanges() {
        int quietLoad = SimulationConfig.quietDay(null).offeredLoadPercent();
        assertTrue(quietLoad >= 50 && quietLoad <= 70,
                "Expected Quiet Day load between 50% and 70%, got " + quietLoad + "%");

        int busyLoad = SimulationConfig.busyDay(null).offeredLoadPercent();
        assertTrue(busyLoad >= 80 && busyLoad <= 100,
                "Expected Busy Day load between 80% and 100%, got " + busyLoad + "%");

        int surgeLoad = SimulationConfig.emergencySurge(null).offeredLoadPercent();
        assertTrue(surgeLoad >= 100 && surgeLoad <= 115,
                "Expected Emergency Surge load between 100% and 115%, got " + surgeLoad + "%");
    }
}

