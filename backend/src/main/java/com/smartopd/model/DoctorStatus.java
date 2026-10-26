package com.smartopd.model;

/**
 * DoctorStatus - Operational availability status of a doctor.
 * 
 * ============================================================================
 * OOP Concept: Domain State Enum
 * ============================================================================
 */
public enum DoctorStatus {
    AVAILABLE("Available"),
    ON_BREAK("On Break"),
    OFF_DUTY("Off Duty");

    private final String displayName;

    DoctorStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DoctorStatus fromString(String text) {
        if (text == null) return AVAILABLE;
        String clean = text.trim();
        for (DoctorStatus s : values()) {
            if (s.displayName.equalsIgnoreCase(clean) || s.name().equalsIgnoreCase(clean)) {
                return s;
            }
        }
        return AVAILABLE;
    }
}
