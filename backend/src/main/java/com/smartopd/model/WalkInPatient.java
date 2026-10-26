package com.smartopd.model;

import com.smartopd.config.Settings;
import java.time.Instant;

/**
 * WalkInPatient - Concrete patient subclass representing same-day routine OPD arrivals.
 * 
 * ============================================================================
 * OOP Concept: Inheritance & Dynamic Polymorphism
 * - Implements urgencyBonus(Instant now) returning 0.0 (no scheduled appointment).
 * - Implements fairnessThresholdMinutes(Settings s) returning the walkin threshold.
 * ============================================================================
 */
public class WalkInPatient extends Patient {
    public WalkInPatient(String id, String name, int age, String gender, String phone,
                         Token token, String doctorId, String doctorName, String room,
                         String department, int severity, Instant registeredAt,
                         String chiefComplaint) {
        super(id, name, age, gender, phone, token, doctorId, doctorName, room,
              department, severity, Category.WALK_IN, registeredAt, null, chiefComplaint);
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
