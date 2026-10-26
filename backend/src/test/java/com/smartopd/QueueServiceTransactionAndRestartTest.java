package com.smartopd;

import com.smartopd.config.Settings;
import com.smartopd.dao.ConnectionProvider;
import com.smartopd.events.QueueEvent;
import com.smartopd.model.*;
import com.smartopd.queue.DoctorQueue;
import com.smartopd.service.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueueServiceTransactionAndRestartTest - Tests:
 * - Transaction rollback verification
 * - Full system restart test across new QueueService instances
 * - CallNext, complete consultation, and no-show survival across restart
 * - Multi-threaded concurrency with zero SQLITE_BUSY errors
 */
public class QueueServiceTransactionAndRestartTest {

    @TempDir
    Path tempDir;

    private void setupTestRoster(QueueService service) {
        service.addDepartment("GEN", "General Medicine");
        service.addDepartment("CARD", "Cardiology");
        service.addDoctor(new Doctor("DOC-101", "Dr. Rajesh Mehta", 48, "Male", "9876500101", "General Medicine", "GEN", "OPD Room 102", "09:00 - 16:00", 12, 2));
        service.addDoctor(new Doctor("DOC-102", "Dr. Rohith Sharma", 52, "Male", "9876500102", "Cardiology", "CARD", "OPD Room 108", "09:00 - 15:00", 15, 3));
    }

    @Test
    @DisplayName("Test 7: Transaction rollback: force a failure in the middle of registerPatient and prove no patient, event or sequence change remains")
    void testTransactionRollbackOnFailure() throws Exception {
        String dbPath = tempDir.resolve("test_rollback.db").toString();
        ConnectionProvider cp = new ConnectionProvider(dbPath);
        QueueService service = new QueueService(cp);
        setupTestRoster(service);

        int prePatientCount = countTableRows(cp, "patient");
        int preEventCount = countTableRows(cp, "queue_event");
        int preSeq = getSequenceLastValue(cp, "GEN", "W");
        int preWaitingQueueSize = service.getOrderedQueue("DOC-101").size();

        // Arm failure hook mid-transaction
        service.setPreCommitHook(conn -> {
            throw new RuntimeException("Simulated mid-transaction database crash!");
        });

        PatientRegistrationRequest badRequest = new PatientRegistrationRequest(
                "Rollback Test Patient", 35, "Male", "9876543210",
                Category.WALK_IN, 2, "DOC-101", null, "Headache"
        );

        assertThrows(RuntimeException.class, () -> service.registerPatient(badRequest));

        // Disarm failure hook
        service.setPreCommitHook(null);

        // Verify database state rolled back 100%
        assertEquals(prePatientCount, countTableRows(cp, "patient"), "Patient count in DB must remain unchanged");
        assertEquals(preEventCount, countTableRows(cp, "queue_event"), "Event count in DB must remain unchanged");
        assertEquals(preSeq, getSequenceLastValue(cp, "GEN", "W"), "Token sequence must not increment on rollback");

        // Verify in-memory state did not retain failed registration
        assertEquals(preWaitingQueueSize, service.getOrderedQueue("DOC-101").size(), "In-memory queue must not retain patient");
        assertNull(service.getPatientByToken("GEN-W-001"), "Failed patient token must not exist in memory");
    }

    @Test
    @DisplayName("Test 8: RESTART TEST: register an emergency and a walk-in, advance the simulated clock 50 minutes, create a NEW QueueService on the same file and prove the queue order, tokens, events, settings and clock offset are identical, and the next token continues the sequence")
    void testSystemRestartParityAndPersistence() throws Exception {
        String dbPath = tempDir.resolve("test_restart.db").toString();
        ConnectionProvider cp = new ConnectionProvider(dbPath);
        QueueService service1 = new QueueService(cp);
        setupTestRoster(service1);

        // 1. Register an emergency patient for DOC-102 (Cardiology)
        PatientRegistrationRequest emergReq = new PatientRegistrationRequest(
                "Harish Chandra", 62, "Male", "9876543210",
                Category.EMERGENCY, 5, "DOC-102", null, "Acute coronary syndrome"
        );
        var regEmerg = service1.registerPatient(emergReq);
        assertEquals("CARD-E-001", regEmerg.getPatient().getToken().getValue());

        // 2. Register a walk-in patient for DOC-102 (Cardiology)
        PatientRegistrationRequest walkinReq = new PatientRegistrationRequest(
                "Sunil Gavaskar", 44, "Male", "9876543211",
                Category.WALK_IN, 2, "DOC-102", null, "Hypertension review"
        );
        var regWalkin = service1.registerPatient(walkinReq);
        assertEquals("CARD-W-001", regWalkin.getPatient().getToken().getValue());

        // 3. Advance simulated clock 50 minutes
        service1.advanceClock(50);

        // Capture snapshot before restart
        List<Patient> queue1 = service1.getOrderedQueue("DOC-102");
        List<QueueEvent> events1 = service1.getEvents("DOC-102", null, 100);
        Settings settings1 = service1.getSettings();
        long epochSec1 = service1.getNow().getEpochSecond();

        // 4. Create a completely NEW QueueService instance on the exact same database file
        QueueService service2 = new QueueService(new ConnectionProvider(dbPath));

        // 5. Assert queue order and tokens are identical
        List<Patient> queue2 = service2.getOrderedQueue("DOC-102");
        assertEquals(queue1.size(), queue2.size(), "Queue size must be identical across restart");
        for (int i = 0; i < queue1.size(); i++) {
            assertEquals(queue1.get(i).getToken().getValue(), queue2.get(i).getToken().getValue(),
                    "Queue order at position " + (i + 1) + " must match identically");
        }

        // 6. Assert clock offset matches
        assertEquals(epochSec1, service2.getNow().getEpochSecond(), "Clock offset must survive restart");

        // 7. Assert settings match
        assertEquals(settings1.getSeverityWeight(), service2.getSettings().getSeverityWeight(), "Settings must match");
        assertEquals(settings1.getFairnessBoost(), service2.getSettings().getFairnessBoost(), "Fairness boost must match");

        // 8. Assert events match
        List<QueueEvent> events2 = service2.getEvents("DOC-102", null, 100);
        assertEquals(events1.size(), events2.size(), "Event count must match identically");

        // 9. Register a new patient on service2 and verify sequence continues
        PatientRegistrationRequest nextEmerg = new PatientRegistrationRequest(
                "New Emergency Post-Restart", 55, "Female", "9876543212",
                Category.EMERGENCY, 5, "DOC-102", null, "Severe breathlessness"
        );
        var regPostRestart = service2.registerPatient(nextEmerg);
        assertEquals("CARD-E-002", regPostRestart.getPatient().getToken().getValue(),
                "Sequence must continue uninterrupted after restart (CARD-E-002)");

        // 10. Print formatted table contents sample
        printDatabaseSample(cp);
    }

    @Test
    @DisplayName("Test 9: callNext, complete and no-show survive a restart (completed patients are not in the queue, avgConsultMinutes updated)")
    void testConsultationLifecycleSurvivesRestart() throws Exception {
        String dbPath = tempDir.resolve("test_lifecycle.db").toString();
        ConnectionProvider cp = new ConnectionProvider(dbPath);
        QueueService service1 = new QueueService(cp);
        setupTestRoster(service1);

        int initialAvg = service1.getDoctor("DOC-101").getAvgConsultMinutes();

        // Register patient 1 and call into consultation
        PatientRegistrationRequest p1 = new PatientRegistrationRequest(
                "Ramesh Kumar", 52, "Male", "9876543210",
                Category.WALK_IN, 2, "DOC-101", null, "Follow-up"
        );
        service1.registerPatient(p1);
        service1.callNext("DOC-101");

        // Register patient 2
        PatientRegistrationRequest p2 = new PatientRegistrationRequest(
                "Meera Iyer", 38, "Female", "9733344556",
                Category.WALK_IN, 1, "DOC-101", null, "Consultation"
        );
        service1.registerPatient(p2);

        // Advance 15 mins to give consultation duration
        service1.advanceClock(15);

        // Complete active consultation
        Patient completed = service1.completeConsultation("DOC-101");
        assertEquals(PatientStatus.COMPLETED, completed.getStatus());
        int updatedAvg = service1.getDoctor("DOC-101").getAvgConsultMinutes();

        // Call next waiting patient into room
        Patient called = service1.callNext("DOC-101");
        assertEquals(PatientStatus.IN_CONSULTATION, called.getStatus());

        // Register patient 3 and mark no-show
        PatientRegistrationRequest p3 = new PatientRegistrationRequest(
                "Rohan Patil", 41, "Male", "9977788990",
                Category.SCHEDULED, 2, "DOC-101", "10:15", "Routine check"
        );
        var regP3 = service1.registerPatient(p3);
        Patient noShow = service1.markNoShow(regP3.getPatient().getToken().getValue());
        assertEquals(PatientStatus.NO_SHOW, noShow.getStatus());

        // RESTART: Create brand new service on same file
        QueueService service2 = new QueueService(new ConnectionProvider(dbPath));

        // Verify completed patient is NOT in waiting queue
        List<Patient> waiting = service2.getOrderedQueue("DOC-101");
        boolean containsCompleted = waiting.stream().anyMatch(p -> p.getToken().getValue().equals(completed.getToken().getValue()));
        assertFalse(containsCompleted, "Completed patient must not be in waiting queue");

        // Verify active patient in consultation
        DoctorQueue dq = service2.getQueueManager().getDoctorQueue("DOC-101");
        assertNotNull(dq.getActivePatient(), "Active patient must be in consultation");
        assertEquals(called.getToken().getValue(), dq.getActivePatient().getToken().getValue());

        // Verify no-show patient is NOT in waiting queue
        boolean containsNoShow = waiting.stream().anyMatch(p -> p.getToken().getValue().equals(noShow.getToken().getValue()));
        assertFalse(containsNoShow, "No-show patient must not be in waiting queue");

        // Verify smoothed running average consult minutes persisted
        assertEquals(updatedAvg, service2.getDoctor("DOC-101").getAvgConsultMinutes(),
                "Updated avgConsultMinutes must persist across restart");
    }

    @Test
    @DisplayName("Test 11: Concurrency: 8 threads register 40 patients at the same time, all 40 tokens are unique and sequential with no SQLITE_BUSY failures")
    void testConcurrentRegistrationsNoSqliteBusy() throws Exception {
        String dbPath = tempDir.resolve("test_concurrency.db").toString();
        ConnectionProvider cp = new ConnectionProvider(dbPath);
        QueueService service = new QueueService(cp);
        setupTestRoster(service);

        int threadCount = 8;
        int perThreadPatients = 5;
        int totalPatients = threadCount * perThreadPatients; // 40

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalPatients);

        Set<String> generatedTokens = ConcurrentHashMap.newKeySet();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    for (int i = 0; i < perThreadPatients; i++) {
                        try {
                            PatientRegistrationRequest req = new PatientRegistrationRequest(
                                    "Patient-T" + threadId + "-" + i, 30 + i, "Male", "9876500000",
                                    Category.WALK_IN, 2, "DOC-102", null, "Routine checkup"
                            );
                            var result = service.registerPatient(req);
                            generatedTokens.add(result.getPatient().getToken().getValue());
                        } catch (Throwable ex) {
                            exceptions.add(ex);
                        } finally {
                            doneLatch.countDown();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 40 concurrent registrations must complete within 30 seconds");
        assertTrue(exceptions.isEmpty(), "Zero exceptions allowed during concurrent write; caught: " + exceptions);
        assertEquals(40, generatedTokens.size(), "All 40 generated tokens must be distinct");

        // Verify tokens are sequentially 1..40
        for (int i = 1; i <= 40; i++) {
            String expected = String.format("CARD-W-%03d", i);
            assertTrue(generatedTokens.contains(expected), "Missing expected sequential token: " + expected);
        }
    }

    private int countTableRows(ConnectionProvider cp, String tableName) throws SQLException {
        try (Connection conn = cp.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int getSequenceLastValue(ConnectionProvider cp, String deptCode, String catCode) throws SQLException {
        try (Connection conn = cp.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT last_value FROM token_sequence WHERE department_code = ? AND category_code = ?")) {
            ps.setString(1, deptCode);
            ps.setString(2, catCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void printDatabaseSample(ConnectionProvider cp) throws SQLException {
        System.out.println("\n========================================================================================================");
        System.out.println("SAMPLE DATABASE SNAPSHOT (POST-RESTART TEST)");
        System.out.println("========================================================================================================");

        try (Connection conn = cp.getConnection()) {
            System.out.println("\n--- TABLE: patient (sample rows) ---");
            System.out.printf("%-12s | %-12s | %-28s | %-12s | %-4s | %-8s | %-16s | %-7s%n",
                    "ID", "Token", "Name", "Category", "Sev", "Doctor", "Status", "Skipped");
            System.out.println("--------------------------------------------------------------------------------------------------------");

            String patSql = "SELECT id, token, name, category, severity, doctor_id, status, skipped FROM patient ORDER BY registered_at DESC LIMIT 8";
            try (PreparedStatement ps = conn.prepareStatement(patSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.printf("%-12s | %-12s | %-28s | %-12s | %-4d | %-8s | %-16s | %-7d%n",
                            rs.getString("id"),
                            rs.getString("token"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getInt("severity"),
                            rs.getString("doctor_id"),
                            rs.getString("status"),
                            rs.getInt("skipped"));
                }
            }

            System.out.println("\n--- TABLE: queue_event (recent events) ---");
            System.out.printf("%-8s | %-12s | %-8s | %-20s | %s%n", "ID", "Token", "Doctor", "Type", "Reason");
            System.out.println("--------------------------------------------------------------------------------------------------------");

            String eventSql = "SELECT id, token, doctor_id, type, reason FROM queue_event ORDER BY id DESC LIMIT 8";
            try (PreparedStatement ps = conn.prepareStatement(eventSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.printf("%-8d | %-12s | %-8s | %-20s | %s%n",
                            rs.getInt("id"),
                            rs.getString("token"),
                            rs.getString("doctor_id") != null ? rs.getString("doctor_id") : "N/A",
                            rs.getString("type"),
                            rs.getString("reason"));
                }
            }
        }
        System.out.println("========================================================================================================\n");
    }
}
