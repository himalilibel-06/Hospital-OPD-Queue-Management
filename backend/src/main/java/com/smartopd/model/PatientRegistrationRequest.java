package com.smartopd.model;

/**
 * PatientRegistrationRequest - Data Transfer Object capturing front-desk registration inputs.
 * 
 * ============================================================================
 * OOP Concept: Data Transfer Object (DTO) & Parameter Object Pattern
 * ============================================================================
 */
public class PatientRegistrationRequest {
    private String name;
    private int age;
    private String gender;
    private String phone;
    private Category category;
    private int severity;
    private String doctorId;
    private String appointmentTime;
    private String chiefComplaint;

    public PatientRegistrationRequest() {
    }

    public PatientRegistrationRequest(String name, int age, String gender, String phone,
                                      Category category, int severity, String doctorId,
                                      String appointmentTime, String chiefComplaint) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.phone = phone;
        this.category = category;
        this.severity = severity;
        this.doctorId = doctorId;
        this.appointmentTime = appointmentTime;
        this.chiefComplaint = chiefComplaint;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
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
}
