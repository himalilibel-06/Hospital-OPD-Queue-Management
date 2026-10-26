package com.smartopd.queue;

import com.smartopd.model.Doctor;
import com.smartopd.model.DoctorStatus;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * WaitTimeEstimator - Computes estimated appointment start times and waiting queue delays.
 * 
 * ============================================================================
 * OOP Concept: Single Responsibility Principle (SRP)
 * - Isolates wait time calculation logic from patient and doctor models.
 * - Handles doctor paused state when doctor is on break or off duty.
 * ============================================================================
 */
public class WaitTimeEstimator {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.US).withZone(ZoneId.systemDefault());

    public static WaitEstimate estimate(Patient patient, Doctor doctor, Patient activePatient,
                                        List<Patient> waitingQueue, Instant now) {
        if (doctor != null && doctor.getStatus() != DoctorStatus.AVAILABLE) {
            return new WaitEstimate(true, "Paused (" + doctor.getStatus().getDisplayName() + ")", 999, 0, 0);
        }

        if (patient != null && patient.getStatus() == PatientStatus.IN_CONSULTATION) {
            return new WaitEstimate(false, "In Consultation", 0, 0, 0);
        }

        int avgMinutes = doctor != null ? doctor.getAvgConsultMinutes() : 12;

        int baseMinutesAhead = 0;
        if (activePatient != null && activePatient.getConsultationStartTime() != null) {
            long elapsed = Math.max(0, Duration.between(activePatient.getConsultationStartTime(), now).toMinutes());
            baseMinutesAhead = (int) Math.max(2, avgMinutes - elapsed);
        }

        int indexInQueue = -1;
        if (waitingQueue != null && patient != null) {
            for (int i = 0; i < waitingQueue.size(); i++) {
                Patient p = waitingQueue.get(i);
                if (p.getId().equals(patient.getId()) ||
                    (p.getToken() != null && patient.getToken() != null && p.getToken().equals(patient.getToken()))) {
                    indexInQueue = i;
                    break;
                }
            }
        }

        int queueLen = waitingQueue != null ? waitingQueue.size() : 0;
        int position = indexInQueue >= 0 ? indexInQueue + 1 : queueLen + 1;
        int patientsAhead = indexInQueue >= 0 ? indexInQueue : queueLen;

        int waitMinutes = baseMinutesAhead + (patientsAhead * avgMinutes);
        Instant startEstimate = now.plus(Duration.ofMinutes(waitMinutes));

        String estimatedTimeStr;
        if (waitMinutes <= 2 && activePatient == null) {
            estimatedTimeStr = "Next Call (Immediate)";
        } else {
            estimatedTimeStr = String.format("%s (~%dm)", TIME_FORMATTER.format(startEstimate), waitMinutes);
        }

        return new WaitEstimate(false, estimatedTimeStr, waitMinutes, position, patientsAhead);
    }
}
