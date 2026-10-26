package com.smartopd.model;

import java.util.Objects;

/**
 * Department - Clinical specialty department entity (e.g. Cardiology, Pediatrics).
 * 
 * ============================================================================
 * OOP Concept: Encapsulation & Entity Identity
 * ============================================================================
 */
public class Department {
    private final String code;
    private final String name;

    public Department(String code, String name) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Department code cannot be empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Department name cannot be empty.");
        }
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Department that)) return false;
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return code + " - " + name;
    }
}
