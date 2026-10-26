package com.smartopd.model;

import java.util.Objects;

/**
 * UserAccount - System staff account entity for Front Desk Receptionists and Doctors.
 * 
 * ============================================================================
 * OOP Concept: Encapsulation & Secure State Storage
 * - Passwords are never stored in plaintext; only salted PBKDF2 hash digests.
 * - Patients are guests and do not have user accounts.
 * ============================================================================
 */
public class UserAccount {
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final String role; // "RECEPTIONIST" or "DOCTOR"
    private final String displayName;
    private final String doctorId; // nullable

    public UserAccount(String username, String passwordHash, String salt, String role, String displayName, String doctorId) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be empty.");
        }
        if (salt == null || salt.trim().isEmpty()) {
            throw new IllegalArgumentException("Salt cannot be empty.");
        }
        if (role == null || (!role.equals("RECEPTIONIST") && !role.equals("DOCTOR"))) {
            throw new IllegalArgumentException("Role must be 'RECEPTIONIST' or 'DOCTOR'.");
        }
        this.username = username.trim().toLowerCase();
        this.passwordHash = passwordHash.trim();
        this.salt = salt.trim();
        this.role = role.trim();
        this.displayName = displayName != null ? displayName.trim() : username;
        this.doctorId = doctorId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public String getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDoctorId() {
        return doctorId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserAccount that)) return false;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "UserAccount{" + username + ", role=" + role + ", name=" + displayName + "}";
    }
}
