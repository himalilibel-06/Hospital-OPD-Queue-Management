package com.smartopd.model;

import com.smartopd.exception.ValidationException;

/**
 * Doctor - Clinical provider entity managing an OPD consultation room and queue.
 * 
 * ============================================================================
 * OOP Concept: Inheritance (extends Person) & Invariant Encapsulation
 * ============================================================================
 */
public class Doctor extends Person {
    private String department;
    private String deptCode;
    private String room;
    private String workingHours;
    private int avgConsultMinutes = 12;
    private int emergencyBufferSlots = 2;
    private DoctorStatus status = DoctorStatus.AVAILABLE;
    private int consecutiveEmergencies = 0;

    public Doctor(String id, String name, int age, String gender, String phone,
                  String department, String deptCode, String room, String workingHours,
                  int avgConsultMinutes, int emergencyBufferSlots) {
        super(id, name, age, gender, phone);
        this.department = department;
        this.deptCode = deptCode;
        this.room = room;
        this.workingHours = workingHours != null ? workingHours : "09:00 - 14:00";
        setAvgConsultMinutes(avgConsultMinutes);
        setEmergencyBufferSlots(emergencyBufferSlots);
    }

    public Doctor(String id, String name, String department, String deptCode, String room) {
        super(id, name, 45, "Other", "9876500000");
        this.department = department;
        this.deptCode = deptCode;
        this.room = room;
        this.workingHours = "09:00 - 14:00";
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getWorkingHours() {
        return workingHours;
    }

    public void setWorkingHours(String workingHours) {
        this.workingHours = workingHours;
    }

    public int getAvgConsultMinutes() {
        return avgConsultMinutes;
    }

    public void setAvgConsultMinutes(int avgConsultMinutes) {
        if (avgConsultMinutes < 3 || avgConsultMinutes > 60) {
            throw new ValidationException("Average consultation minutes must be between 3 and 60.");
        }
        this.avgConsultMinutes = avgConsultMinutes;
    }

    public int getEmergencyBufferSlots() {
        return emergencyBufferSlots;
    }

    public void setEmergencyBufferSlots(int emergencyBufferSlots) {
        if (emergencyBufferSlots < 0 || emergencyBufferSlots > 10) {
            throw new ValidationException("Emergency buffer slots must be between 0 and 10.");
        }
        this.emergencyBufferSlots = emergencyBufferSlots;
    }

    public DoctorStatus getStatus() {
        return status;
    }

    public void setStatus(DoctorStatus status) {
        this.status = status != null ? status : DoctorStatus.AVAILABLE;
    }

    public int getConsecutiveEmergencies() {
        return consecutiveEmergencies;
    }

    public void setConsecutiveEmergencies(int consecutiveEmergencies) {
        this.consecutiveEmergencies = Math.max(0, consecutiveEmergencies);
    }

    public void incrementConsecutiveEmergencies() {
        this.consecutiveEmergencies++;
    }

    public void resetConsecutiveEmergencies() {
        this.consecutiveEmergencies = 0;
    }
}
