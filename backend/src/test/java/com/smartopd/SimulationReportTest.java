package com.smartopd;

import com.smartopd.config.Settings;
import com.smartopd.simulation.SimulationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimulationReportTest - Verifies that the Java 30-seed 3-preset aggregate metrics
 * match the JavaScript tune.js executable specification within 0.05.
 */
public class SimulationReportTest {

    private static final double TOLERANCE = 0.05;

    @Test
    @DisplayName("Report: Java 30-seed 3-preset aggregate matches tune.js within 0.05")
    void test30SeedBenchmarkMatchesTuneJs() {
        Settings settings = new Settings(); // default 100, 3, 150, 45, 45, cap 2
        SimulationReport.ReportSummary summary = SimulationReport.run30SeedReport(settings);

        assertEquals(90, summary.totalRuns());

        // FCFS: emergency 6.86, walk-in 6.74, scheduled 6.61, max scheduled 18.89, starvation 0.00
        SimulationReport.PolicyAggregate fcfs = summary.fcfs();
        assertEquals(6.86, fcfs.avgEmergencyWait(), TOLERANCE, "FCFS emergency wait mismatch");
        assertEquals(6.74, fcfs.avgWalkinWait(), TOLERANCE, "FCFS walk-in wait mismatch");
        assertEquals(6.61, fcfs.avgScheduledWait(), TOLERANCE, "FCFS scheduled wait mismatch");
        assertEquals(18.89, fcfs.maxScheduledWait(), TOLERANCE, "FCFS max scheduled wait mismatch");
        assertEquals(0.00, fcfs.starvationCount(), TOLERANCE, "FCFS starvation mismatch");

        // Strict Priority: emergency 1.07, walk-in 9.02, scheduled 8.34, max scheduled 43.31, starvation 0.44
        SimulationReport.PolicyAggregate strict = summary.strictPriority();
        assertEquals(1.07, strict.avgEmergencyWait(), TOLERANCE, "Strict Priority emergency wait mismatch");
        assertEquals(9.02, strict.avgWalkinWait(), TOLERANCE, "Strict Priority walk-in wait mismatch");
        assertEquals(8.34, strict.avgScheduledWait(), TOLERANCE, "Strict Priority scheduled wait mismatch");
        assertEquals(43.31, strict.maxScheduledWait(), TOLERANCE, "Strict Priority max scheduled wait mismatch");
        assertEquals(0.44, strict.starvationCount(), TOLERANCE, "Strict Priority starvation mismatch");

        // SmartOPD: emergency 1.27, walk-in 10.32, scheduled 6.78, max scheduled 28.59, starvation 0.00
        SimulationReport.PolicyAggregate smart = summary.smartopd();
        assertEquals(1.27, smart.avgEmergencyWait(), TOLERANCE, "SmartOPD emergency wait mismatch");
        assertEquals(10.32, smart.avgWalkinWait(), TOLERANCE, "SmartOPD walk-in wait mismatch");
        assertEquals(6.78, smart.avgScheduledWait(), TOLERANCE, "SmartOPD scheduled wait mismatch");
        assertEquals(28.59, smart.maxScheduledWait(), TOLERANCE, "SmartOPD max scheduled wait mismatch");
        assertEquals(0.00, smart.starvationCount(), TOLERANCE, "SmartOPD starvation mismatch");
    }
}
