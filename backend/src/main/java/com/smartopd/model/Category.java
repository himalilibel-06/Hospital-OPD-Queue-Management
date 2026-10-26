package com.smartopd.model;

/**
 * Category - Patient OPD arrival category enum.
 * 
 * ============================================================================
 * OOP Concept: Typesafe Enums with Code Mapping
 * ============================================================================
 */
public enum Category {
    EMERGENCY("Emergency", "E"),
    SCHEDULED("Scheduled", "S"),
    WALK_IN("Walk-in", "W");

    private final String displayName;
    private final String code;

    Category(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public static Category fromString(String text) {
        if (text == null) return WALK_IN;
        String clean = text.trim();
        for (Category c : values()) {
            if (c.displayName.equalsIgnoreCase(clean) || c.name().equalsIgnoreCase(clean) || c.code.equalsIgnoreCase(clean)) {
                return c;
            }
        }
        return WALK_IN;
    }
}
