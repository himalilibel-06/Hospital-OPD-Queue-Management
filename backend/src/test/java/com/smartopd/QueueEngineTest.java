package com.smartopd;

import com.smartopd.config.Settings;
import com.smartopd.events.QueueEvent;
import com.smartopd.model.Category;
import com.smartopd.model.Doctor;
import com.smartopd.model.DoctorStatus;
import com.smartopd.model.EmergencyPatient;
import com.smartopd.model.EventType;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientRegistrationRequest;
import com.smartopd.model.Token;
import com.smartopd.model.WalkInPatient;
import com.smartopd.queue.DoctorQueue;
import com.smartopd.queue.QueueManager;
import com.smartopd.queue.RegistrationResult;
import com.smartopd.scoring.SmartOpdStrategy;
import com.smartopd.time.SimulatedClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueueEngineTest - Verifies queue ranking, emergency override, fairness aging,
 * anti-starvation cap, and no-show handling.
 * 
 * Ported directly from frontend/js/tests/run-tests.js (Tests 1 to 5).
 */
public class QueueEngineTest {

    private QueueManager queueManager;
    private SimulatedClock clock;
    private Doctor doc105;
    private Doctor doc101;
    private Doctor doc103;

    @BeforeEach
    void setUp() {
        clock = new SimulatedClock(Instant.parse("2026-10-22T09:00:00Z"));
        Settings settings = new Settings();
        SmartOpdStrategy strategy = new SmartOpdStrategy();
        queueManager = new QueueManager(clock, settings, strategy);

        doc105 = new Doctor("DOC-105", "Dr. Suresh Verma", "ENT", "ENT", "OPD Room 102");
        doc101 = new Doctor("DOC-101", "Dr. Rajesh Mehta", "General Medicine", "GEN", "OPD Room 101");
        doc103 = new Doctor("DOC-103", "Dr. Priya Nair", "Pediatrics", "PED", "OPD Room 104");

        queueManager.registerDoctor(doc105);
        queueManager.registerDoctor(doc101);
        queueManager.registerDoctor(doc103);
    }

    @Test
    @DisplayName("Test 1: Emergency Override Ordering and Explanation Text")
    void testEmergencyOverrideOrdering() {
        // Register routine walk-in first
        PatientRegistrationRequest walkinReq = new PatientRegistrationRequest(
                "Anil Walkin", 35, "Male", "9876500001",
                Category.WALK_IN, 2, "DOC-105", null, "Routine ear pain"
        );
        RegistrationResult walkinRes = queueManager.registerPatient(walkinReq);

        // Register Level 5 critical emergency second
        PatientRegistrationRequest emergReq = new PatientRegistrationRequest(
                "Sunita Emergency", 50, "Female", "9876500002",
                Category.EMERGENCY, 5, "DOC-105", null, "Acute respiratory distress"
        );
        RegistrationResult emergRes = queueManager.registerPatient(emergReq);

        DoctorQueue queue = queueManager.getDoctorQueue("DOC-105");
        List<Patient> ordered = queue.getOrdered(clock.now());

        assertEquals(2, ordered.size());
        assertEquals(emergRes.getPatient().getId(), ordered.get(0).getId(),
                "Level 5 Emergency must take Position #1 over existing walk-in");
        assertTrue(emergRes.getExplanation().contains("Severity 5 emergency override"),
                "Emergency patient explanation must state 'Severity 5 emergency override'");
    }

    @Test
    @DisplayName("Test 2: Fairness Boost after advancing clock past 45m threshold")
    void testFairnessBoostAfterClockAdvance() {
        PatientRegistrationRequest schedReq = new PatientRegistrationRequest(
                "Tarun Verma", 40, "Male", "9876500003",
                Category.SCHEDULED, 1, "DOC-101", "11:00", "Follow-up check"
        );
        RegistrationResult res = queueManager.registerPatient(schedReq);
        Patient patient = res.getPatient();

        // 1. At 0 minutes wait, patient must not be fairness boosted
        boolean initiallyBoosted = queueManager.getStrategy().isFairnessBoosted(patient, clock.now(), queueManager.getSettings());
        assertFalse(initiallyBoosted, "Scheduled patient should not be fairness boosted at 0 min wait");

        // 2. Advance clock by 50 minutes (exceeds 45m threshold)
        queueManager.advanceClock(50);
        Instant nowAfter50 = clock.now();

        boolean boostedAfter50 = queueManager.getStrategy().isFairnessBoosted(patient, nowAfter50, queueManager.getSettings());
        assertTrue(boostedAfter50, "Scheduled patient achieves Fairness Boost after 50 minutes wait (threshold: 45m)");

        double scoreAfter50 = queueManager.getStrategy().calculateScore(patient, nowAfter50, queueManager.getSettings());
        // Score = Severity(1*100) + Wait(50*3) + FairnessBoost(150) = >= 400
        assertTrue(scoreAfter50 >= (1 * 100) + (50 * 3) + 150,
                "Fairness boost (+150 pts) reflects in priority score calculation");

        // 3. Verify FAIRNESS_BOOST event logged in audit eventLog
        List<QueueEvent> events = queueManager.getEvents("DOC-101", patient.getToken().getValue(), 10);
        boolean fairnessEventLogged = events.stream().anyMatch(e -> e.getType() == EventType.FAIRNESS_BOOST);
        assertTrue(fairnessEventLogged, "FAIRNESS_BOOST event must be logged into audit eventLog");

        // Verify it is logged only once even if clock advances further
        int boostCount1 = (int) events.stream().filter(e -> e.getType() == EventType.FAIRNESS_BOOST).count();
        queueManager.advanceClock(10);
        List<QueueEvent> eventsAfterMore = queueManager.getEvents("DOC-101", patient.getToken().getValue(), 10);
        int boostCount2 = (int) eventsAfterMore.stream().filter(e -> e.getType() == EventType.FAIRNESS_BOOST).count();
        assertEquals(boostCount1, boostCount2, "FAIRNESS_BOOST event must be logged at most once per patient");
    }

    @Test
    @DisplayName("Test 3: Emergency Cap Rule (MAX_CONSECUTIVE_EMERGENCIES = 2)")
    void testEmergencyCapRule() {
        DoctorQueue queue = queueManager.getDoctorQueue("DOC-103");
        doc103.setConsecutiveEmergencies(2); // Cap of 2 consecutive emergencies reached

        // Add a routine Walk-in patient registered 46 minutes ago (exceeded 45m threshold, score 388 < 400)
        Instant registered46mAgo = clock.now().minus(Duration.ofMinutes(46));
        WalkInPatient starvingPatient = new WalkInPatient(
                "PAT-STARVE-1", "Patient Starving", 8, "Female", "9876599998",
                new Token("PED", "W", 998), "DOC-103", doc103.getName(), doc103.getRoom(),
                "Pediatrics", 1, registered46mAgo, "Routine fever"
        );
        queue.addPatient(starvingPatient);

        // Add an urgent Level 4 Emergency arrived right now (0m wait, score 400 > 388)
        EmergencyPatient urgentEmerg = new EmergencyPatient(
                "PAT-EMERG-4", "Urgent Sev 4", 6, "Male", "9876599999",
                new Token("PED", "E", 999), "DOC-103", doc103.getName(), doc103.getRoom(),
                "Pediatrics", 4, clock.now(), "High trauma pain"
        );
        queue.addPatient(urgentEmerg);

        // Verify Sev 4 is naturally top by score, but emergency cap rule forces starving patient to be called!
        Patient called = queueManager.callNext("DOC-103");
        assertEquals(starvingPatient.getId(), called.getId(),
                "Fairness cap must serve starving patient after 2 consecutive emergencies when top patient is Sev 4");

        List<QueueEvent> logs = queueManager.getEvents("DOC-103", null, 5);
        boolean fairnessCapLogged = logs.stream().anyMatch(e -> e.getReason().contains("Fairness cap"));
        assertTrue(fairnessCapLogged, "Fairness cap action must be logged with plain-English explanation");

        // Verify Severity 5 unconditional override still bypasses the cap:
        doc103.setConsecutiveEmergencies(2);
        queue.completeConsultation(clock.now(), queueManager.getEventLog());

        WalkInPatient anotherStarving = new WalkInPatient(
                "PAT-STARVE-2", "Starving 2", 9, "Male", "9876599997",
                new Token("PED", "W", 997), "DOC-103", doc103.getName(), doc103.getRoom(),
                "Pediatrics", 1, clock.now().minus(Duration.ofMinutes(50)), "Ear ache"
        );
        queue.addPatient(anotherStarving);

        EmergencyPatient criticalSev5 = new EmergencyPatient(
                "PAT-CRIT-5", "Critical Sev 5", 4, "Male", "9876599996",
                new Token("PED", "E", 996), "DOC-103", doc103.getName(), doc103.getRoom(),
                "Pediatrics", 5, clock.now(), "Cardiac arrest"
        );
        queue.addPatient(criticalSev5);

        Patient calledSev5 = queueManager.callNext("DOC-103");
        assertEquals(criticalSev5.getId(), calledSev5.getId(),
                "Severity 5 critical emergency must unconditionally override even when cap=2 is reached");
    }

    @Test
    @DisplayName("Test 4: No-Show Frees Slot & Shifts Remaining Queue")
    void testNoShowFreesSlotAndShiftsQueue() {
        PatientRegistrationRequest r1 = new PatientRegistrationRequest("Patient One", 30, "M", "9876500011", Category.WALK_IN, 2, "DOC-105", null, "Cough");
        PatientRegistrationRequest r2 = new PatientRegistrationRequest("Patient Two", 32, "F", "9876500012", Category.WALK_IN, 2, "DOC-105", null, "Sore throat");

        RegistrationResult res1 = queueManager.registerPatient(r1);
        RegistrationResult res2 = queueManager.registerPatient(r2);

        DoctorQueue queue = queueManager.getDoctorQueue("DOC-105");
        assertEquals(res1.getPatient().getId(), queue.getOrdered(clock.now()).get(0).getId());

        // Mark first patient as No-Show
        queueManager.markNoShow(res1.getToken().getValue());

        List<Patient> remaining = queue.getOrdered(clock.now());
        assertEquals(1, remaining.size());
        assertEquals(res2.getPatient().getId(), remaining.get(0).getId(),
                "Second patient must shift up to Position #1 after No-Show");

        List<QueueEvent> events = queueManager.getEvents("DOC-105", res1.getToken().getValue(), 5);
        assertTrue(events.stream().anyMatch(e -> e.getType() == EventType.NO_SHOW),
                "NO_SHOW event must be recorded in audit trail");
    }

    @Test
    @DisplayName("Test 5: Tie-break Deterministically by Registration Time (FIFO)")
    void testTieBreakByRegistrationTime() {
        Instant t0 = clock.now();
        Instant t1 = t0.plus(Duration.ofMinutes(1));

        WalkInPatient p1 = new WalkInPatient("P1", "First Arrived", 25, "M", "9876500021",
                new Token("GEN", "W", 101), "DOC-101", doc101.getName(), doc101.getRoom(), "General", 2, t0, "Checkup");

        WalkInPatient p2 = new WalkInPatient("P2", "Second Arrived", 28, "F", "9876500022",
                new Token("GEN", "W", 102), "DOC-101", doc101.getName(), doc101.getRoom(), "General", 2, t1, "Checkup");

        DoctorQueue queue = queueManager.getDoctorQueue("DOC-101");
        queue.addPatient(p2); // add later arrival first
        queue.addPatient(p1); // add earlier arrival second

        List<Patient> ordered = queue.getOrdered(t1);
        assertEquals("P1", ordered.get(0).getId(),
                "Patients with identical scores must be ordered strictly by earlier registration time (FIFO)");
        assertEquals("P2", ordered.get(1).getId());
    }
}
