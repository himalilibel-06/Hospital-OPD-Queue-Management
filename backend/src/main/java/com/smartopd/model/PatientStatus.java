package com.smartopd.model;

/**
 * PatientStatus - Current clinical workflow state of an OPD patient.
 * 
 * ============================================================================
 * OOP Concept: Domain State Enum
 * ============================================================================
 */
public enum PatientStatus {
    WAITING("Waiting"),
    IN_CONSULTATION("In Consultation"),
    COMPLETED("Completed"),
    NO_SHOW("No Show"),
    SKIPPED("Skipped");

    private final String displayName;

    PatientStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PatientStatus fromString(String text) {
        if (text == null) return WAITING;
        String clean = text.trim();
        for (PatientStatus s : values()) {
            if (s.displayName.equalsIgnoreCase(clean) || s.name().equalsIgnoreCase(clean)) {
                return s;
            }
        }
        return WAITING;
    }
}
