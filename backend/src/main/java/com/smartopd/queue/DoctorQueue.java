package com.smartopd.queue;

import com.smartopd.config.Settings;
import com.smartopd.events.EventLog;
import com.smartopd.events.QueueEvent;
import com.smartopd.exception.DoctorUnavailableException;
import com.smartopd.exception.ValidationException;
import com.smartopd.model.Category;
import com.smartopd.model.Doctor;
import com.smartopd.model.DoctorStatus;
import com.smartopd.model.EventType;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientStatus;
import com.smartopd.scoring.PriorityStrategy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DoctorQueue - Encapsulates the waiting queue and consultation room state for a single doctor.
 * 
 * ============================================================================
 * OOP Concept: Encapsulation, State Management & Invariant Protection
 * - Maintains the collection of waiting patients.
 * - Implements the emergency cap rule, priority ordering, and consultation lifecycle.
 * ============================================================================
 */
public class DoctorQueue {
    private final Doctor doctor;
    private final List<Patient> waitingPatients = new CopyOnWriteArrayList<>();
    private Patient activePatient = null;
    private PriorityStrategy strategy;
    private Settings settings;

    public DoctorQueue(Doctor doctor, PriorityStrategy strategy, Settings settings) {
        this.doctor = doctor;
        this.strategy = strategy;
        this.settings = settings;
    }

    /**
     * Returns waiting patients ordered by score DESC, with ties broken by registration time ASC (FIFO).
     */
    public List<Patient> getOrdered(Instant now) {
        List<Patient> list = new ArrayList<>(waitingPatients);
        list.sort((a, b) -> {
            double scoreA = strategy.calculateScore(a, now, settings);
            double scoreB = strategy.calculateScore(b, now, settings);
            if (Double.compare(scoreB, scoreA) != 0) {
                return Double.compare(scoreB, scoreA);
            }
            return a.getRegisteredAt().compareTo(b.getRegisteredAt());
        });
        return Collections.unmodifiableList(list);
    }

    public synchronized void addPatient(Patient patient) {
        if (patient != null && !waitingPatients.contains(patient)) {
            waitingPatients.add(patient);
        }
    }

    public synchronized Patient callNext(Instant now, EventLog eventLog) {
        if (doctor.getStatus() != DoctorStatus.AVAILABLE) {
            throw new DoctorUnavailableException(
                    "Doctor " + doctor.getName() + " is currently " + doctor.getStatus().getDisplayName() + ". Cannot call patients."
            );
        }

        if (activePatient != null) {
            throw new DoctorUnavailableException(
                    "Doctor " + doctor.getName() + " is currently consulting "
                    + activePatient.getToken().getValue() + " (" + activePatient.getName() + "). Complete current consultation first."
            );
        }

        List<Patient> queue = getOrdered(now);
        if (queue.isEmpty()) {
            throw new ValidationException("No patients currently waiting for " + doctor.getName() + ".");
        }

        EmergencyCapSelector.SelectionResult capResult = EmergencyCapSelector.selectNextPatient(
                queue,
                doctor.getConsecutiveEmergencies(),
                settings.getMaxConsecutiveEmergencies(),
                strategy,
                now,
                settings
        );

        Patient selected = capResult.selectedPatient();
        boolean fairnessCapTriggered = capResult.fairnessCapTriggered();

        // Update consecutive emergency counter on doctor
        if (fairnessCapTriggered) {
            doctor.resetConsecutiveEmergencies();
        } else {
            if (selected.getSeverity() == 5 || selected.getCategory() == Category.EMERGENCY) {
                doctor.incrementConsecutiveEmergencies();
            } else {
                doctor.resetConsecutiveEmergencies();
            }
        }

        waitingPatients.remove(selected);
        selected.setStatus(PatientStatus.IN_CONSULTATION);
        selected.setConsultationStartTime(now);
        this.activePatient = selected;

        String callReason = fairnessCapTriggered
                ? "Fairness cap: 2 emergencies served in a row, longest-waiting patient called (" + selected.getToken().getValue() + ")"
                : "Token " + selected.getToken().getValue() + " (" + selected.getName() + ") called into "
                  + doctor.getRoom() + " by " + doctor.getName() + ".";

        if (eventLog != null) {
            eventLog.log(new QueueEvent(EventType.CALLED, selected.getToken().getValue(), doctor.getId(), callReason, now));
        }

        return selected;
    }

    public synchronized Patient completeConsultation(Instant now, EventLog eventLog) {
        if (activePatient == null) {
            throw new DoctorUnavailableException("No active consultation in progress for " + doctor.getName() + ".");
        }

        Patient completed = activePatient;
        completed.setStatus(PatientStatus.COMPLETED);
        completed.setConsultationEndTime(now);

        long actualMins = 8;
        if (completed.getConsultationStartTime() != null) {
            actualMins = Math.max(1, Duration.between(completed.getConsultationStartTime(), now).toMinutes());
        }

        // Running average consult duration smoothing (weight 0.3)
        double updatedAvg = (doctor.getAvgConsultMinutes() * 0.7) + (actualMins * 0.3);
        doctor.setAvgConsultMinutes((int) Math.max(3, Math.min(60, Math.round(updatedAvg))));

        if (eventLog != null) {
            eventLog.log(new QueueEvent(
                    EventType.COMPLETED,
                    completed.getToken().getValue(),
                    doctor.getId(),
                    "Consultation completed for " + completed.getToken().getValue() + " (" + completed.getName()
                    + ") in " + actualMins + "m. Running avg updated to " + doctor.getAvgConsultMinutes() + "m.",
                    now
            ));
        }

        this.activePatient = null;
        return completed;
    }

    public synchronized Patient markNoShow(String tokenNumber, Instant now, EventLog eventLog) {
        Patient target = null;
        for (Patient p : waitingPatients) {
            if (p.getToken() != null && p.getToken().getValue().equalsIgnoreCase(tokenNumber.trim())) {
                target = p;
                break;
            }
        }

        if (target == null) {
            throw new ValidationException("Token " + tokenNumber + " not found.");
        }

        target.setStatus(PatientStatus.NO_SHOW);
        target.setNoShowTime(now);
        waitingPatients.remove(target);

        if (eventLog != null) {
            eventLog.log(new QueueEvent(
                    EventType.NO_SHOW,
                    target.getToken().getValue(),
                    doctor.getId(),
                    "Token " + target.getToken().getValue() + " (" + target.getName() + ") marked as No-Show. Slot freed in " + doctor.getRoom() + ".",
                    now
            ));
        }

        return target;
    }

    public synchronized Patient skipPatient(String tokenNumber, String reason, Instant now, EventLog eventLog) {
        Patient target = null;
        for (Patient p : waitingPatients) {
            if (p.getToken() != null && p.getToken().getValue().equalsIgnoreCase(tokenNumber.trim())) {
                target = p;
                break;
            }
        }

        if (target == null) {
            throw new ValidationException("Token " + tokenNumber + " not found.");
        }

        target.setSkipped(true);
        target.setRegisteredAt(now); // Push to back of current tier

        String skipReason = reason != null && !reason.trim().isEmpty() ? reason.trim() : "Patient temporarily unavailable";

        if (eventLog != null) {
            eventLog.log(new QueueEvent(
                    EventType.SKIPPED,
                    target.getToken().getValue(),
                    doctor.getId(),
                    "Token " + target.getToken().getValue() + " (" + target.getName() + ") skipped: " + skipReason + ". Moved down in queue.",
                    now
            ));
        }

        return target;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public List<Patient> getWaitingPatients() {
        return Collections.unmodifiableList(new ArrayList<>(waitingPatients));
    }

    public Patient getActivePatient() {
        return activePatient;
    }

    public void setActivePatient(Patient activePatient) {
        this.activePatient = activePatient;
    }

    public PriorityStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(PriorityStrategy strategy) {
        this.strategy = strategy;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }
}
