package com.smartopd.api.security;

import java.time.Instant;

/**
 * UserSession - Represents an active authenticated session.
 * 
 * ============================================================================
 * OOP Concept: Value Object / Immutable Session Record
 * - Stores bearer token, user credentials, role, doctorId binding, and expiry.
 * ============================================================================
 */
public record UserSession(
        String token,
        String username,
        String role,
        String displayName,
        String doctorId,
        Instant createdAt,
        Instant expiresAt
) {
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
