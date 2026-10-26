package com.smartopd.simulation;

import com.smartopd.config.Settings;
import com.smartopd.model.Category;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientStatus;
import com.smartopd.model.Token;
import java.time.Duration;
import java.time.Instant;

/**
 * SimulatedPatient - Patient subclass modeling synthetic patients during discrete-event queue simulations.
 * 
 * ============================================================================
 * OOP Concept: Inheritance, Polymorphism & Strategy Interoperability
 * - Extends Patient to participate directly in PriorityStrategy polymorphic scoring
 *   without duplicating scoring or triage rules.
 * - Dynamically computes urgencyBonus(now) and fairnessThresholdMinutes(settings)
 *   in discrete simulation minutes relative to an arbitrary simulation base instant.
 * ============================================================================
 */
public class SimulatedPatient extends Patient {

    private final int index;
    private final int arrivalMinute;
    private final Integer appointmentMinute;
    private final int consultDuration;
    private final Instant baseInstant;

    private int startMinute;
    private int finishMinute;
    private int waitMinutes;

    public SimulatedPatient(
            String id,
            int index,
            Category category,
            int severity,
            int arrivalMinute,
            Integer appointmentMinute,
            int consultDuration,
            Instant baseInstant
    ) {
        super(
                id,
                "Patient " + (index + 1),
                35,
                "Other",
                "9876543210",
                new Token("SIM", "P", index + 1),
                "DOC-SIM",
                "Simulation Doctor",
                "Room 101",
                "General Medicine",
                severity,
                category,
                baseInstant.plusSeconds(arrivalMinute * 60L),
                appointmentMinute != null ? String.format("%02d:%02d", (appointmentMinute / 60) % 24, appointmentMinute % 60) : null,
                "Synthetic simulation encounter"
        );
        this.index = index;
        this.arrivalMinute = arrivalMinute;
        this.appointmentMinute = appointmentMinute;
        this.consultDuration = consultDuration;
        this.baseInstant = baseInstant;
    }

    /**
     * Polymorphic appointment urgency bonus calculation matching JavaScript simulator.js:
     * - If arrival has passed appointment time (diff <= 0): max bonus (+60.0 pts)
     * - If within 30 minutes of appointment: linear ramp ((30 - diff) * 1.5 pts)
     * - Otherwise: 0.0 pts
     */
    @Override
    public double urgencyBonus(Instant now) {
        if (getCategory() != Category.SCHEDULED || appointmentMinute == null || now == null) {
            return 0.0;
        }
        long currentMinute = Duration.between(baseInstant, now).toMinutes();
        long diff = appointmentMinute - currentMinute;
        if (diff <= 0) {
            return 60.0;
        } else if (diff <= 30) {
            return (30 - diff) * 1.5;
        }
        return 0.0;
    }

    /**
     * Anti-starvation threshold lookup based on patient category and hospital settings.
     */
    @Override
    public int fairnessThresholdMinutes(Settings settings) {
        if (settings == null) return 45;
        if (getCategory() == Category.SCHEDULED) {
            return settings.getFairnessThresholdScheduled();
        }
        return settings.getFairnessThresholdWalkin();
    }

    /**
     * Creates an unserved, isolated clone of this synthetic patient for running multiple policies.
     */
    public SimulatedPatient createCopy() {
        return new SimulatedPatient(
                getId(),
                this.index,
                getCategory(),
                getSeverity(),
                this.arrivalMinute,
                this.appointmentMinute,
                this.consultDuration,
                this.baseInstant
        );
    }

    public int getIndex() {
        return index;
    }

    public int getArrivalMinute() {
        return arrivalMinute;
    }

    public Integer getAppointmentMinute() {
        return appointmentMinute;
    }

    public int getConsultDuration() {
        return consultDuration;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public void setStartMinute(int startMinute) {
        this.startMinute = startMinute;
    }

    public int getFinishMinute() {
        return finishMinute;
    }

    public void setFinishMinute(int finishMinute) {
        this.finishMinute = finishMinute;
    }

    public int getWaitMinutes() {
        return waitMinutes;
    }

    public void setWaitMinutes(int waitMinutes) {
        this.waitMinutes = waitMinutes;
    }

    public Instant getBaseInstant() {
        return baseInstant;
    }
}
