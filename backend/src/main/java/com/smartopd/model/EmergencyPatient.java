package com.smartopd.model;

import com.smartopd.config.Settings;
import com.smartopd.exception.InvalidSeverityException;
import java.time.Instant;

/**
 * EmergencyPatient - Concrete patient subclass representing critical triage arrivals.
 * 
 * ============================================================================
 * OOP Concept: Inheritance, Subclass Specialization & Dynamic Polymorphism
 * - Enforces the hospital triage constraint: Emergency requires Severity Level 4 or 5.
 * - Implements urgencyBonus(Instant now) returning 0.0 (emergencies use clinical severity/override).
 * - Implements fairnessThresholdMinutes(Settings s) returning the routine walkin threshold.
 * ============================================================================
 */
public class EmergencyPatient extends Patient {
    public EmergencyPatient(String id, String name, int age, String gender, String phone,
                            Token token, String doctorId, String doctorName, String room,
                            String department, int severity, Instant registeredAt,
                            String chiefComplaint) {
        super(id, name, age, gender, phone, token, doctorId, doctorName, room,
              department, severity, Category.EMERGENCY, registeredAt, null, chiefComplaint);

        if (severity < 4) {
            throw new InvalidSeverityException("Emergency category requires Severity Level 4 or 5.");
        }
    }

    @Override
    public double urgencyBonus(Instant now) {
        return 0.0;
    }

    @Override
    public int fairnessThresholdMinutes(Settings settings) {
        return settings != null ? settings.getFairnessThresholdWalkin() : 45;
    }
}
