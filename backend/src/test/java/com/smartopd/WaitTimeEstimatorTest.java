package com.smartopd;

import com.smartopd.model.Doctor;
import com.smartopd.model.DoctorStatus;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientStatus;
import com.smartopd.model.Token;
import com.smartopd.model.WalkInPatient;
import com.smartopd.queue.WaitEstimate;
import com.smartopd.queue.WaitTimeEstimator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WaitTimeEstimatorTest - Verifies wait-time estimation for known queue states
 * and doctor availability pauses.
 */
public class WaitTimeEstimatorTest {

    @Test
    @DisplayName("Test 9: Wait-time estimate for known queue and Paused for unavailable doctor")
    void testWaitTimeEstimate() {
        Doctor doctor = new Doctor("DOC-102", "Dr. Rohith Sharma", "Cardiology", "CARD", "Room 108");
        doctor.setAvgConsultMinutes(10);
        Instant now = Instant.parse("2026-10-22T10:00:00Z");

        // Active patient currently in consultation (started 4m ago -> remaining = 10 - 4 = 6m)
        WalkInPatient active = new WalkInPatient(
                "P-ACTIVE", "Active Patient", 55, "Male", "9876500001",
                new Token("CARD", "W", 1), doctor.getId(), doctor.getName(), doctor.getRoom(),
                doctor.getDepartment(), 3, now.minus(Duration.ofMinutes(20)), "Angina"
        );
        active.setStatus(PatientStatus.IN_CONSULTATION);
        active.setConsultationStartTime(now.minus(Duration.ofMinutes(4)));

        // Waiting queue: 2 patients ahead, then our target patient (index 2, position 3)
        WalkInPatient ahead1 = new WalkInPatient("P-A1", "Ahead 1", 40, "F", "9876500002",
                new Token("CARD", "W", 2), doctor.getId(), doctor.getName(), doctor.getRoom(), doctor.getDepartment(), 2, now.minus(Duration.ofMinutes(15)), "ECG");
        WalkInPatient ahead2 = new WalkInPatient("P-A2", "Ahead 2", 42, "M", "9876500003",
                new Token("CARD", "W", 3), doctor.getId(), doctor.getName(), doctor.getRoom(), doctor.getDepartment(), 2, now.minus(Duration.ofMinutes(10)), "BP");
        WalkInPatient target = new WalkInPatient("P-TARGET", "Target Patient", 48, "F", "9876500004",
                new Token("CARD", "W", 4), doctor.getId(), doctor.getName(), doctor.getRoom(), doctor.getDepartment(), 2, now.minus(Duration.ofMinutes(5)), "Checkup");

        List<Patient> queue = new ArrayList<>();
        queue.add(ahead1);
        queue.add(ahead2);
        queue.add(target);

        // Expected wait: 6 (active remaining) + 2 patients ahead * 10m = 26 minutes
        WaitEstimate estimate = WaitTimeEstimator.estimate(target, doctor, active, queue, now);
        assertFalse(estimate.isPaused());
        assertEquals(26, estimate.getWaitMinutes(), "Expected 26 minutes wait (6m active + 2*10m ahead)");
        assertEquals(3, estimate.getPosition(), "Target should be at position 3");
        assertEquals(2, estimate.getPatientsAhead(), "Should have 2 patients ahead");
        assertTrue(estimate.getEstimatedTimeStr().contains("~26m"));

        // Doctor on break -> Paused
        doctor.setStatus(DoctorStatus.ON_BREAK);
        WaitEstimate pausedBreak = WaitTimeEstimator.estimate(target, doctor, active, queue, now);
        assertTrue(pausedBreak.isPaused());
        assertTrue(pausedBreak.getEstimatedTimeStr().contains("Paused (On Break)"));

        // Doctor off duty -> Paused
        doctor.setStatus(DoctorStatus.OFF_DUTY);
        WaitEstimate pausedOff = WaitTimeEstimator.estimate(target, doctor, active, queue, now);
        assertTrue(pausedOff.isPaused());
        assertTrue(pausedOff.getEstimatedTimeStr().contains("Paused (Off Duty)"));
    }
}
