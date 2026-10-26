package com.smartopd.model;

import com.smartopd.config.Settings;
import com.smartopd.exception.InvalidSeverityException;
import java.time.Instant;

/**
 * Patient - Abstract representation of an OPD patient registered into a queue.
 * 
 * ============================================================================
 * OOP Concept: Abstraction, Inheritance & Dynamic Polymorphism
 * - Extends Person to inherit demographic attributes and validation.
 * - Declares abstract methods urgencyBonus(now) and fairnessThresholdMinutes(settings)
 *   that concrete subclasses implement polymorphically based on clinical triage category.
 * ============================================================================
 */
public abstract class Patient extends Person {
    private Token token;
    private String doctorId;
    private String doctorName;
    private String room;
    private String department;
    private int severity;
    private Category category;
    private Instant registeredAt;
    private String appointmentTime;
    private String chiefComplaint;
    private PatientStatus status = PatientStatus.WAITING;
    private boolean skipped = false;
    private Instant consultationStartTime;
    private Instant consultationEndTime;
    private Instant noShowTime;

    public Patient(String id, String name, int age, String gender, String phone,
                   Token token, String doctorId, String doctorName, String room,
                   String department, int severity, Category category,
                   Instant registeredAt, String appointmentTime, String chiefComplaint) {
        super(id, name, age, gender, phone);
        this.token = token;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.room = room;
        this.department = department;
        setSeverity(severity);
        this.category = category;
        this.registeredAt = registeredAt != null ? registeredAt : Instant.now();
        this.appointmentTime = appointmentTime;
        this.chiefComplaint = chiefComplaint != null ? chiefComplaint : "Consultation request";
    }

    /**
     * Polymorphic method returning category-specific appointment proximity bonus.
     * ScheduledPatient computes proximity to appointment time; others return 0.0.
     * Demonstrates dynamic method dispatch during OOP evaluations.
     * 
     * @param now current simulation or real-time instant
     * @return urgency bonus points
     */
    public abstract double urgencyBonus(Instant now);

    /**
     * Polymorphic method returning the anti-starvation fairness threshold minutes.
     * 
     * @param settings hospital scoring settings
     * @return waiting time threshold in minutes
     */
    public abstract int fairnessThresholdMinutes(Settings settings);

    public Token getToken() {
        return token;
    }

    public void setToken(Token token) {
        this.token = token;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        if (severity < 1 || severity > 5) {
            throw new InvalidSeverityException("Severity must be between 1 and 5.");
        }
        this.severity = severity;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(String appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public PatientStatus getStatus() {
        return status;
    }

    public void setStatus(PatientStatus status) {
        this.status = status;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public Instant getConsultationStartTime() {
        return consultationStartTime;
    }

    public void setConsultationStartTime(Instant consultationStartTime) {
        this.consultationStartTime = consultationStartTime;
    }

    public Instant getConsultationEndTime() {
        return consultationEndTime;
    }

    public void setConsultationEndTime(Instant consultationEndTime) {
        this.consultationEndTime = consultationEndTime;
    }

    public Instant getNoShowTime() {
        return noShowTime;
    }

    public void setNoShowTime(Instant noShowTime) {
        this.noShowTime = noShowTime;
    }
}
