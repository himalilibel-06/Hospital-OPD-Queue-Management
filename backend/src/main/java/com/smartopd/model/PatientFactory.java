package com.smartopd.model;

import com.smartopd.exception.DoctorUnavailableException;
import com.smartopd.exception.InvalidSeverityException;
import com.smartopd.exception.ValidationException;
import java.time.Instant;
import java.util.UUID;

/**
 * PatientFactory - Creational Factory Pattern encapsulating patient subclass instantiation.
 * 
 * ============================================================================
 * OOP Concept: Factory Method Design Pattern (Creational)
 * - Decouples client callers from concrete patient subclasses.
 * - Enforces triage validation rules before instantiating concrete types:
 *     1. Emergency category requires Severity Level 4 or 5.
 *     2. Doctor must not be Off Duty.
 *     3. Scheduled patients must have a valid appointment time.
 * ============================================================================
 */
public class PatientFactory {

    /**
     * Factory method creating the appropriate Patient subclass.
     * 
     * @param request front-desk registration inputs
     * @param doctor assigned OPD doctor
     * @param token allocated queue token
     * @param registeredAt registration timestamp
     * @return concrete Patient instance (EmergencyPatient, ScheduledPatient, or WalkInPatient)
     */
    public static Patient createPatient(PatientRegistrationRequest request, Doctor doctor, Token token, Instant registeredAt) {
        if (request == null) {
            throw new ValidationException("Registration request cannot be null.");
        }
        if (doctor == null) {
            throw new ValidationException("Please select an active OPD doctor.");
        }
        if (doctor.getStatus() == DoctorStatus.OFF_DUTY) {
            throw new DoctorUnavailableException("Doctor " + doctor.getName() + " is currently Off Duty. Cannot assign new patients.");
        }

        // Validate common fields
        String name = request.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Patient full name is required.");
        }

        int age = request.getAge();
        if (age <= 0 || age > 130) {
            throw new ValidationException("Please enter a valid patient age.");
        }

        String phone = request.getPhone();
        if (phone == null) {
            throw new ValidationException("Please enter a valid 10-digit mobile number.");
        }
        String cleanPhone = phone.trim().replaceAll("[-+\\s]", "");
        if (!cleanPhone.matches("^\\d{10}$")) {
            throw new ValidationException("Please enter a valid 10-digit mobile number.");
        }

        int severity = request.getSeverity();
        if (severity < 1 || severity > 5) {
            throw new InvalidSeverityException("Severity must be between 1 and 5.");
        }

        Category category = request.getCategory() != null ? request.getCategory() : Category.WALK_IN;
        if (category == Category.EMERGENCY && severity < 4) {
            throw new InvalidSeverityException("Emergency category requires Severity Level 4 or 5.");
        }

        String id = "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant regTime = registeredAt != null ? registeredAt : Instant.now();
        String complaint = request.getChiefComplaint() != null && !request.getChiefComplaint().trim().isEmpty()
                ? request.getChiefComplaint().trim()
                : "Consultation request";

        switch (category) {
            case EMERGENCY:
                return new EmergencyPatient(
                        id, name, age, request.getGender(), cleanPhone,
                        token, doctor.getId(), doctor.getName(), doctor.getRoom(),
                        doctor.getDepartment(), severity, regTime, complaint
                );

            case SCHEDULED:
                String apptTime = request.getAppointmentTime();
                if (apptTime == null || apptTime.trim().isEmpty()) {
                    throw new ValidationException("Please select an appointment time slot for scheduled visits.");
                }
                return new ScheduledPatient(
                        id, name, age, request.getGender(), cleanPhone,
                        token, doctor.getId(), doctor.getName(), doctor.getRoom(),
                        doctor.getDepartment(), severity, regTime, apptTime.trim(), complaint
                );

            case WALK_IN:
            default:
                return new WalkInPatient(
                        id, name, age, request.getGender(), cleanPhone,
                        token, doctor.getId(), doctor.getName(), doctor.getRoom(),
                        doctor.getDepartment(), severity, regTime, complaint
                );
        }
    }

    /**
     * Reconstructs an existing Patient subclass instance from persistent storage.
     * Preserves original ID, token, timestamps, lifecycle status, and skipped state.
     * 
     * @return concrete Patient subclass (EmergencyPatient, ScheduledPatient, or WalkInPatient)
     */
    public static Patient reconstructPatient(
            String id, String name, int age, String gender, String phone,
            Token token, String doctorId, String doctorName, String room,
            String department, int severity, Category category,
            Instant registeredAt, String appointmentTime, String chiefComplaint,
            PatientStatus status, boolean skipped,
            Instant consultStart, Instant consultEnd) {

        Patient patient;
        Category cat = category != null ? category : Category.WALK_IN;
        switch (cat) {
            case EMERGENCY:
                patient = new EmergencyPatient(
                        id, name, age, gender, phone,
                        token, doctorId, doctorName, room,
                        department, severity, registeredAt, chiefComplaint
                );
                break;
            case SCHEDULED:
                patient = new ScheduledPatient(
                        id, name, age, gender, phone,
                        token, doctorId, doctorName, room,
                        department, severity, registeredAt,
                        appointmentTime != null ? appointmentTime : "09:00", chiefComplaint
                );
                break;
            case WALK_IN:
            default:
                patient = new WalkInPatient(
                        id, name, age, gender, phone,
                        token, doctorId, doctorName, room,
                        department, severity, registeredAt, chiefComplaint
                );
                break;
        }

        if (status != null) {
            patient.setStatus(status);
        }
        patient.setSkipped(skipped);
        patient.setConsultationStartTime(consultStart);
        patient.setConsultationEndTime(consultEnd);

        return patient;
    }
}
