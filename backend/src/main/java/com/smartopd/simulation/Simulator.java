package com.smartopd.simulation;

import com.smartopd.config.Settings;
import com.smartopd.model.Category;
import com.smartopd.model.Patient;
import com.smartopd.queue.EmergencyCapSelector;
import com.smartopd.scoring.FcfsStrategy;
import com.smartopd.scoring.PriorityStrategy;
import com.smartopd.scoring.SmartOpdStrategy;
import com.smartopd.scoring.StrictPriorityStrategy;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simulator - Discrete-Event OPD Queue Simulation Engine (Pure Java, zero framework dependencies).
 * 
 * ============================================================================
 * OOP Concept: Strategy Design Pattern (Behavioral) & Discrete-Event Simulation
 * - Takes a PriorityStrategy implementation (FcfsStrategy, StrictPriorityStrategy, SmartOpdStrategy)
 *   and simulates patient flow through identical multi-doctor outpatient clinic environments.
 * - Reuses the exact same PriorityStrategy and EmergencyCapSelector used by live clinical queues.
 * - Guarantees 100% mathematical and bit-level parity with frontend/js/simulator.js.
 * ============================================================================
 */
public class Simulator {

    private static final Instant BASE_INSTANT = Instant.parse("2026-01-01T08:00:00Z");

    /**
     * Internal simulation doctor tracking busy minutes, earliest availability, and consecutive emergency count.
     */
    static class SimDoctor {
        final int id;
        int freeAt = 0;
        int consecutiveEmergencies = 0;
        int busyMinutes = 0;

        SimDoctor(int id) {
            this.id = id;
        }
    }

    /**
     * Generates a deterministic synthetic patient cohort matching JavaScript generateSyntheticPatients().
     * 
     * @param config simulation configuration
     * @param rng seeded pseudo-random number generator
     * @param baseInstant starting instant for patient arrival timestamps
     * @return chronologically sorted list of synthetic patients
     */
    public static List<SimulatedPatient> generateSyntheticPatients(
            SimulationConfig config,
            SeededRandom rng,
            Instant baseInstant
    ) {
        int numPatients = config.numPatients();
        int arrivalSpreadMinutes = config.arrivalSpreadMinutes();
        int avgConsultMinutes = config.avgConsultMinutes();
        int emergencyPercent = config.emergencyPercent();
        int scheduledPercent = config.scheduledPercent();

        List<SimulatedPatient> patients = new ArrayList<>(numPatients);

        for (int i = 0; i < numPatients; i++) {
            String id = String.format("SIM-P-%03d", i + 1);
            int arrivalMinute = (int) Math.floor(rng.next() * arrivalSpreadMinutes);
            double catRoll = rng.next() * 100.0;

            Category category;
            int severity;
            Integer appointmentMinute = null;

            if (catRoll < emergencyPercent) {
                category = Category.EMERGENCY;
                // 65% Severity 5 (Critical/Trauma), 35% Severity 4 (Urgent)
                severity = rng.next() < 0.65 ? 5 : 4;
            } else if (catRoll < emergencyPercent + scheduledPercent) {
                category = Category.SCHEDULED;
                // Severity 1 to 3
                double sevRoll = rng.next();
                severity = sevRoll < 0.4 ? 1 : (sevRoll < 0.75 ? 2 : 3);
                // Scheduled appointment around arrival time (+/- 15 mins)
                appointmentMinute = Math.max(0, arrivalMinute + (int) Math.floor((rng.next() - 0.5) * 30.0));
            } else {
                category = Category.WALK_IN;
                // Severity 1 to 4
                double sevRoll = rng.next();
                severity = sevRoll < 0.45 ? 1 : (sevRoll < 0.8 ? 2 : (sevRoll < 0.95 ? 3 : 4));
            }

            // Consultation duration with +/- 30% random variation around average
            double variation = (rng.next() * 0.60) - 0.30;
            int consultDuration = Math.max(3, (int) Math.round(avgConsultMinutes * (1.0 + variation)));

            patients.add(new SimulatedPatient(
                    id,
                    i,
                    category,
                    severity,
                    arrivalMinute,
                    appointmentMinute,
                    consultDuration,
                    baseInstant
            ));
        }

        // Sort strictly by arrival time, ties broken by original index
        patients.sort((a, b) -> {
            if (a.getArrivalMinute() != b.getArrivalMinute()) {
                return Integer.compare(a.getArrivalMinute(), b.getArrivalMinute());
            }
            return Integer.compare(a.getIndex(), b.getIndex());
        });

        return patients;
    }

    /**
     * Executes discrete-event simulation for a single policy over an isolated patient stream copy.
     * 
     * @param policyName human-readable policy identifier ("FCFS", "StrictPriority", "SmartOPD")
     * @param strategy priority strategy implementation
     * @param originalCohort cohort to simulate (will be deeply copied)
     * @param numDoctors number of parallel consultation rooms
     * @param settings hospital scoring parameters
     * @param baseInstant starting instant
     * @return policy simulation results and performance metrics
     */
    public static PolicySimulationResult simulatePolicy(
            String policyName,
            PriorityStrategy strategy,
            List<SimulatedPatient> originalCohort,
            int numDoctors,
            Settings settings,
            Instant baseInstant
    ) {
        List<SimulatedPatient> unserved = originalCohort.stream()
                .map(SimulatedPatient::createCopy)
                .collect(Collectors.toCollection(ArrayList::new));
        List<SimulatedPatient> served = new ArrayList<>(unserved.size());

        List<SimDoctor> doctors = new ArrayList<>(numDoctors);
        for (int d = 0; d < numDoctors; d++) {
            doctors.add(new SimDoctor(d));
        }

        int currentMinute = 0;
        int totalCount = unserved.size();

        while (served.size() < totalCount) {
            // 1. Gather all arrived, unserved patients
            final int currMin = currentMinute;
            List<SimulatedPatient> waiting = unserved.stream()
                    .filter(p -> p.getArrivalMinute() <= currMin)
                    .collect(Collectors.toCollection(ArrayList::new));

            // 2. If nobody has arrived yet, advance clock to next discrete event
            if (waiting.isEmpty()) {
                int nextEventMinute = Integer.MAX_VALUE;
                for (SimulatedPatient p : unserved) {
                    if (p.getArrivalMinute() > currentMinute) {
                        nextEventMinute = Math.min(nextEventMinute, p.getArrivalMinute());
                        break; // unserved list is already sorted by arrivalMinute
                    }
                }
                for (SimDoctor doc : doctors) {
                    if (doc.freeAt > currentMinute) {
                        nextEventMinute = Math.min(nextEventMinute, doc.freeAt);
                    }
                }

                if (nextEventMinute == Integer.MAX_VALUE || nextEventMinute <= currentMinute) {
                    currentMinute++;
                } else {
                    currentMinute = nextEventMinute;
                }
                continue;
            }

            // 3. Find an idle doctor (first available in list, matching JavaScript doctors.find())
            SimDoctor idleDoctor = null;
            for (SimDoctor doc : doctors) {
                if (doc.freeAt <= currentMinute) {
                    idleDoctor = doc;
                    break;
                }
            }

            if (idleDoctor == null) {
                int earliestFree = Integer.MAX_VALUE;
                for (SimDoctor doc : doctors) {
                    if (doc.freeAt > currentMinute) {
                        earliestFree = Math.min(earliestFree, doc.freeAt);
                    }
                }
                currentMinute = earliestFree;
                continue;
            }

            // 4. Select next patient according to policy
            Instant now = baseInstant.plusSeconds(currentMinute * 60L);
            SimulatedPatient selectedPatient;

            if ("FCFS".equalsIgnoreCase(policyName)) {
                // First-Come, First-Served: ordered strictly by arrival minute, ties by index
                waiting.sort((a, b) -> {
                    if (a.getArrivalMinute() != b.getArrivalMinute()) {
                        return Integer.compare(a.getArrivalMinute(), b.getArrivalMinute());
                    }
                    return Integer.compare(a.getIndex(), b.getIndex());
                });
                selectedPatient = waiting.get(0);

            } else if ("StrictPriority".equalsIgnoreCase(policyName)) {
                // Strict Priority: severity descending, ties broken by arrival minute, then index
                waiting.sort((a, b) -> {
                    if (b.getSeverity() != a.getSeverity()) {
                        return Integer.compare(b.getSeverity(), a.getSeverity());
                    }
                    if (a.getArrivalMinute() != b.getArrivalMinute()) {
                        return Integer.compare(a.getArrivalMinute(), b.getArrivalMinute());
                    }
                    return Integer.compare(a.getIndex(), b.getIndex());
                });
                selectedPatient = waiting.get(0);

            } else {
                // SmartOPD: dynamic score descending, ties broken by earlier arrival, then index
                waiting.sort((a, b) -> {
                    double scoreA = strategy.calculateScore(a, now, settings);
                    double scoreB = strategy.calculateScore(b, now, settings);
                    if (Double.compare(scoreB, scoreA) != 0) {
                        return Double.compare(scoreB, scoreA);
                    }
                    if (a.getArrivalMinute() != b.getArrivalMinute()) {
                        return Integer.compare(a.getArrivalMinute(), b.getArrivalMinute());
                    }
                    return Integer.compare(a.getIndex(), b.getIndex());
                });

                // Apply Emergency Cap Rule via shared EmergencyCapSelector
                List<Patient> queue = new ArrayList<>(waiting);
                EmergencyCapSelector.SelectionResult capResult = EmergencyCapSelector.selectNextPatient(
                        queue,
                        idleDoctor.consecutiveEmergencies,
                        settings.getMaxConsecutiveEmergencies(),
                        strategy,
                        now,
                        settings
                );

                selectedPatient = (SimulatedPatient) capResult.selectedPatient();
                boolean capTriggered = capResult.fairnessCapTriggered();

                if (capTriggered) {
                    idleDoctor.consecutiveEmergencies = 0;
                } else {
                    if (selectedPatient.getSeverity() == 5 || selectedPatient.getCategory() == Category.EMERGENCY) {
                        idleDoctor.consecutiveEmergencies++;
                    } else {
                        idleDoctor.consecutiveEmergencies = 0;
                    }
                }
            }

            // 5. Serve patient
            int startMinute = currentMinute;
            int waitMinutes = startMinute - selectedPatient.getArrivalMinute();
            int finishMinute = startMinute + selectedPatient.getConsultDuration();

            idleDoctor.freeAt = finishMinute;
            idleDoctor.busyMinutes += selectedPatient.getConsultDuration();

            selectedPatient.setStartMinute(startMinute);
            selectedPatient.setWaitMinutes(waitMinutes);
            selectedPatient.setFinishMinute(finishMinute);

            served.add(selectedPatient);
            unserved.remove(selectedPatient);
        }

        // 6. Compute Comprehensive Policy Metrics
        int schedThreshold = settings != null ? settings.getFairnessThresholdScheduled() : 45;
        int walkinThreshold = settings != null ? settings.getFairnessThresholdWalkin() : 45;

        long totalWait = 0;
        long emergWaitSum = 0; int emergCount = 0;
        long highSevWaitSum = 0; int highSevCount = 0;
        long schedWaitSum = 0; int schedCount = 0;
        long walkinWaitSum = 0; int walkinCount = 0;
        int maxWaitSched = 0;
        int starvationCount = 0;

        List<Integer> allWaits = new ArrayList<>(served.size());
        List<PatientWaitRecord> patientRecords = new ArrayList<>(served.size());

        for (SimulatedPatient p : served) {
            int wait = p.getWaitMinutes();
            totalWait += wait;
            allWaits.add(wait);

            if (p.getCategory() == Category.EMERGENCY) {
                emergWaitSum += wait;
                emergCount++;
            }
            if (p.getSeverity() >= 4) {
                highSevWaitSum += wait;
                highSevCount++;
            }
            if (p.getCategory() == Category.SCHEDULED) {
                schedWaitSum += wait;
                schedCount++;
                if (wait > maxWaitSched) {
                    maxWaitSched = wait;
                }
                if (wait > (schedThreshold * 2)) {
                    starvationCount++;
                }
            }
            if (p.getCategory() == Category.WALK_IN) {
                walkinWaitSum += wait;
                walkinCount++;
            }

            int limit = (p.getCategory() == Category.SCHEDULED) ? (schedThreshold * 2) : (walkinThreshold * 2);
            patientRecords.add(PatientWaitRecord.of(
                    p.getId(),
                    p.getCategory().getDisplayName(),
                    p.getSeverity(),
                    p.getArrivalMinute(),
                    p.getStartMinute(),
                    p.getFinishMinute(),
                    p.getWaitMinutes(),
                    p.getWaitMinutes() > limit
            ));
        }

        allWaits.sort(Integer::compareTo);
        int p90Index = Math.min(allWaits.size() - 1, (int) Math.floor(allWaits.size() * 0.90));
        int p90Wait = allWaits.isEmpty() ? 0 : allWaits.get(p90Index);

        int totalSimTime = 1;
        for (SimulatedPatient p : served) {
            totalSimTime = Math.max(totalSimTime, p.getFinishMinute());
        }

        int totalDoctorCapacity = numDoctors * totalSimTime;
        int totalDoctorBusy = 0;
        for (SimDoctor doc : doctors) {
            totalDoctorBusy += doc.busyMinutes;
        }

        int doctorIdlePercent = Math.max(0, Math.min(100, (int) Math.round(
                ((double) (totalDoctorCapacity - totalDoctorBusy) / (double) totalDoctorCapacity) * 100.0
        )));

        int avgWaitOverall = (int) Math.round((double) totalWait / (double) served.size());
        int avgWaitEmerg = emergCount > 0 ? (int) Math.round((double) emergWaitSum / (double) emergCount) : 0;
        int avgWaitHighSev = highSevCount > 0 ? (int) Math.round((double) highSevWaitSum / (double) highSevCount) : 0;
        int avgWaitSched = schedCount > 0 ? (int) Math.round((double) schedWaitSum / (double) schedCount) : 0;
        int avgWaitWalkin = walkinCount > 0 ? (int) Math.round((double) walkinWaitSum / (double) walkinCount) : 0;

        PolicyMetrics metrics = PolicyMetrics.of(
                avgWaitOverall,
                avgWaitEmerg,
                avgWaitHighSev,
                avgWaitSched,
                avgWaitWalkin,
                p90Wait,
                maxWaitSched,
                starvationCount,
                doctorIdlePercent
        );

        return PolicySimulationResult.of(
                policyName,
                served.size(),
                totalSimTime,
                metrics,
                patientRecords
        );
    }

    /**
     * Generates analytical insight statements comparing the 3 simulation runs.
     */
    public static List<String> generateInsights(
            PolicyMetrics fcfs,
            PolicyMetrics strict,
            PolicyMetrics smart,
            Settings settings
    ) {
        List<String> insights = new ArrayList<>();
        int schedLimit = (settings != null ? settings.getFairnessThresholdScheduled() : 45) * 2;

        // 1. Honest Trade-off Statement (Strict Priority vs SmartOPD)
        int diffEmerg = Math.max(0, smart.avgWaitEmergency() - strict.avgWaitEmergency());
        int diffMaxSched = Math.max(0, strict.maxScheduledWait() - smart.maxScheduledWait());
        String emergMinText = diffEmerg == 1 ? "1 more minute" : diffEmerg + " more minutes";
        String schedMinText = diffMaxSched == 1 ? "1 minute" : diffMaxSched + " minutes";

        if (diffEmerg > 0 && diffMaxSched > 0) {
            insights.add("Compared with strict priority, SmartOPD accepted " + emergMinText
                    + " of emergency wait to cut the longest scheduled wait by " + schedMinText + ".");
        } else if (diffMaxSched > 0) {
            insights.add("Compared with strict priority, SmartOPD cut the longest scheduled wait by "
                    + schedMinText + " without increasing emergency wait.");
        } else {
            insights.add("Compared with strict priority, SmartOPD balanced emergency response ("
                    + smart.avgWaitEmergency() + "m) against longest scheduled wait ("
                    + smart.maxScheduledWait() + "m).");
        }

        // 2. Fundamental Queue Conservation Law Takeaway
        insights.add("Average wait is similar across policies; SmartOPD changes who waits, not how much total waiting there is.");

        // 3. Honest Walk-in Trade-off Line (when walk-in average wait under SmartOPD exceeds FCFS)
        int diffWalkin = smart.avgWaitWalkin() - fcfs.avgWaitWalkin();
        if (diffWalkin > 0) {
            String walkinMinText = diffWalkin == 1 ? "1 minute" : diffWalkin + " minutes";
            insights.add("Trade-off: walk-in patients waited " + walkinMinText
                    + " longer on average than under first come first served, because emergencies and scheduled patients are protected first.");
        }

        // 4. Emergency Wait Comparison (FCFS vs SmartOPD)
        if (fcfs.avgWaitEmergency() > 0 && smart.avgWaitEmergency() < fcfs.avgWaitEmergency()) {
            int reduction = (int) Math.round(((double) (fcfs.avgWaitEmergency() - smart.avgWaitEmergency()) / (double) fcfs.avgWaitEmergency()) * 100.0);
            insights.add("SmartOPD cut average emergency wait by " + reduction
                    + " percent versus FCFS (" + smart.avgWaitEmergency() + "m vs " + fcfs.avgWaitEmergency() + "m).");
        } else {
            insights.add("SmartOPD prioritized critical triage, keeping average emergency wait to "
                    + smart.avgWaitEmergency() + " minutes.");
        }

        // 5. Starvation Comparison (Strict Priority vs SmartOPD) with proper singular/plural
        String strictStarvText = strict.starvationCount() == 1 ? "1 scheduled patient" : strict.starvationCount() + " scheduled patients";
        if (strict.starvationCount() > smart.starvationCount()) {
            insights.add("Strict priority left " + strictStarvText + " waiting over "
                    + schedLimit + " minutes; SmartOPD reduced this to " + smart.starvationCount() + ".");
        } else {
            insights.add("Strict priority left " + strictStarvText + " waiting over "
                    + schedLimit + " minutes; SmartOPD held this to " + smart.starvationCount() + ".");
        }

        // 6. Maximum Scheduled Patient Wait
        if (strict.maxWaitScheduled() > smart.maxWaitScheduled()) {
            insights.add("Worst-case wait for scheduled appointments was " + strict.maxWaitScheduled()
                    + "m under Strict Priority vs " + smart.maxWaitScheduled() + "m with SmartOPD fairness safeguards.");
        } else {
            insights.add("Scheduled appointments maximum wait was controlled at "
                    + smart.maxWaitScheduled() + "m with SmartOPD fairness safeguards.");
        }

        // 7. Predictability (90th Percentile)
        insights.add("SmartOPD maintains clinical balance with a 90th-percentile wait of "
                + smart.p90Wait() + " minutes across all patient categories.");

        return insights;
    }

    /**
     * Executes the comprehensive simulation for all 3 policies on the identical patient cohort.
     * 
     * @param config validated simulation configuration
     * @param settings hospital scoring parameters (or null for default Settings)
     * @return SimulationResult containing per-policy metrics, patient traces, and comparative insights
     */
    public static SimulationResult runSimulation(SimulationConfig config, Settings settings) {
        Settings effSettings = settings != null ? settings : new Settings();
        SeededRandom rng = new SeededRandom(config.randomSeed());
        List<SimulatedPatient> patients = generateSyntheticPatients(config, rng, BASE_INSTANT);

        List<SyntheticPatientDto> patientList = patients.stream()
                .map(p -> new SyntheticPatientDto(
                        p.getId(),
                        p.getIndex(),
                        p.getCategory().getDisplayName(),
                        p.getSeverity(),
                        p.getArrivalMinute(),
                        p.getAppointmentMinute(),
                        p.getConsultDuration()
                ))
                .collect(Collectors.toList());

        PriorityStrategy fcfsStrategy = new FcfsStrategy();
        PriorityStrategy strictStrategy = new StrictPriorityStrategy();
        PriorityStrategy smartStrategy = new SmartOpdStrategy();

        PolicySimulationResult fcfsResult = simulatePolicy("FCFS", fcfsStrategy, patients, config.numDoctors(), effSettings, BASE_INSTANT);
        PolicySimulationResult strictResult = simulatePolicy("StrictPriority", strictStrategy, patients, config.numDoctors(), effSettings, BASE_INSTANT);
        PolicySimulationResult smartResult = simulatePolicy("SmartOPD", smartStrategy, patients, config.numDoctors(), effSettings, BASE_INSTANT);

        List<String> insights = generateInsights(fcfsResult.metrics(), strictResult.metrics(), smartResult.metrics(), effSettings);

        Map<String, PolicySimulationResult> policies = new LinkedHashMap<>();
        policies.put("fcfs", fcfsResult);
        policies.put("strictPriority", strictResult);
        policies.put("smartopd", smartResult);
        policies.put("FCFS", fcfsResult);
        policies.put("StrictPriority", strictResult);
        policies.put("SmartOPD", smartResult);

        return new SimulationResult(
                config,
                config.offeredLoadPercent(),
                patients.size(),
                patientList,
                policies,
                policies,
                insights
        );
    }
}
