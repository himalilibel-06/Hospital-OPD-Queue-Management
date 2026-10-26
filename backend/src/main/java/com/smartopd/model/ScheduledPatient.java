package com.smartopd.model;

import com.smartopd.config.Settings;
import com.smartopd.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ScheduledPatient - Concrete patient subclass representing pre-booked OPD appointments.
 * 
 * ============================================================================
 * OOP Concept: Inheritance & Behavioral Specialization (Dynamic Polymorphism)
 * - Enforces mandatory appointment slot presence.
 * - Implements urgencyBonus(Instant now) implementing the appointment proximity rule.
 * - Implements fairnessThresholdMinutes(Settings s) returning the scheduled threshold.
 * ============================================================================
 */
public class ScheduledPatient extends Patient {
    public ScheduledPatient(String id, String name, int age, String gender, String phone,
                            Token token, String doctorId, String doctorName, String room,
                            String department, int severity, Instant registeredAt,
                            String appointmentTime, String chiefComplaint) {
        super(id, name, age, gender, phone, token, doctorId, doctorName, room,
              department, severity, Category.SCHEDULED, registeredAt, appointmentTime, chiefComplaint);

        if (appointmentTime == null || appointmentTime.trim().isEmpty()) {
            throw new ValidationException("Please select an appointment time slot for scheduled visits.");
        }
    }

    @Override
    public double urgencyBonus(Instant now) {
        String apptTime = getAppointmentTime();
        if (apptTime == null || !apptTime.contains(":") || now == null) {
            return 0.0;
        }
        try {
            String[] parts = apptTime.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int min = Integer.parseInt(parts[1].trim());

            ZonedDateTime nowZoned = now.atZone(ZoneId.systemDefault());
            ZonedDateTime apptZoned = nowZoned.withHour(hour).withMinute(min).withSecond(0).withNano(0);
            long diffMinutes = Duration.between(nowZoned, apptZoned).toMinutes();

            if (diffMinutes <= 0) {
                return 60.0;
            } else if (diffMinutes <= 30) {
                return Math.max(0, 30 - diffMinutes) * 1.5;
            }
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    @Override
    public int fairnessThresholdMinutes(Settings settings) {
        return settings != null ? settings.getFairnessThresholdScheduled() : 45;
    }
}
