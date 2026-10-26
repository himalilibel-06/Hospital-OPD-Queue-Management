package com.smartopd.api.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionStore - In-memory bearer token store with 8-hour expiry
 * and 5-attempts/5-minutes brute-force account lockout protection.
 * 
 * ============================================================================
 * OOP & Security Concepts:
 * 1. Cryptographic Token Generation: 256-bit entropy generated via SecureRandom.
 * 2. Sliding Window Rate Limiting: Tracks failed login attempts per username.
 *    5 failures within 300 seconds triggers a 60-second lockout.
 * 3. State Invalidation: Safe concurrent session cleanup and invalidation.
 * ============================================================================
 */
@Component
public class SessionStore {

    private static final Duration SESSION_DURATION = Duration.ofHours(8);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(1);

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UserFailureTracker> failureTrackers = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Helper tracker for failed attempts and lockout expiration.
     */
    private static class UserFailureTracker {
        final List<Instant> failures = Collections.synchronizedList(new ArrayList<>());
        volatile Instant lockedUntil = null;
    }

    /**
     * Checks whether an account is currently locked due to too many failed attempts.
     */
    public void checkLockout(String username) {
        if (username == null) return;
        String clean = username.trim().toLowerCase();
        UserFailureTracker tracker = failureTrackers.get(clean);
        if (tracker != null && tracker.lockedUntil != null) {
            Instant now = Instant.now();
            if (now.isBefore(tracker.lockedUntil)) {
                long remainingSeconds = Duration.between(now, tracker.lockedUntil).toSeconds();
                throw new AccountLockedException(
                        "Too many failed login attempts for user '" + username + "'. Account is locked. Please try again in " + remainingSeconds + " second(s)."
                );
            } else {
                // Lockout period expired
                tracker.lockedUntil = null;
                tracker.failures.clear();
            }
        }
    }

    /**
     * Records a failed login attempt. If threshold reached, triggers 1-minute lockout.
     */
    public void recordFailedAttempt(String username) {
        if (username == null) return;
        String clean = username.trim().toLowerCase();
        UserFailureTracker tracker = failureTrackers.computeIfAbsent(clean, k -> new UserFailureTracker());
        Instant now = Instant.now();

        synchronized (tracker) {
            // Prune attempts older than 5 minutes
            Instant cutoff = now.minus(WINDOW_DURATION);
            tracker.failures.removeIf(instant -> instant.isBefore(cutoff));
            tracker.failures.add(now);

            if (tracker.failures.size() >= MAX_FAILED_ATTEMPTS) {
                tracker.lockedUntil = now.plus(LOCK_DURATION);
                long seconds = Duration.between(now, tracker.lockedUntil).toSeconds();
                throw new AccountLockedException(
                        "Too many failed login attempts for user '" + username + "'. Account is locked for 1 minute. Please try again in " + seconds + " second(s)."
                );
            }
        }
    }

    /**
     * Clears failure history on successful authentication.
     */
    public void recordSuccess(String username) {
        if (username == null) return;
        String clean = username.trim().toLowerCase();
        failureTrackers.remove(clean);
    }

    /**
     * Creates and stores a new random 256-bit bearer token session valid for 8 hours.
     */
    public UserSession createSession(String username, String role, String displayName, String doctorId) {
        byte[] randomBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(randomBytes);
        String token = HexFormat.of().formatHex(randomBytes);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(SESSION_DURATION);

        UserSession session = new UserSession(token, username, role, displayName, doctorId, now, expiresAt);
        sessions.put(token, session);
        return session;
    }

    /**
     * Retrieves an active session, removing it if expired.
     */
    public Optional<UserSession> getSession(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        UserSession session = sessions.get(token);
        if (session == null) return Optional.empty();

        if (session.isExpired(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Invalidates an active session (logout).
     */
    public boolean invalidateSession(String token) {
        if (token == null) return false;
        return sessions.remove(token) != null;
    }

    /**
     * Invalidates all active sessions for a specific username.
     */
    public void invalidateSessionsForUser(String username) {
        if (username == null) return;
        String target = username.trim().toLowerCase();
        sessions.entrySet().removeIf(entry -> entry.getValue().username().equalsIgnoreCase(target));
    }

    /**
     * Invalidates all active sessions for a specific username except the specified current token.
     */
    public void invalidateOtherSessionsForUser(String username, String currentToken) {
        if (username == null) return;
        String target = username.trim().toLowerCase();
        sessions.entrySet().removeIf(entry ->
                entry.getValue().username().equalsIgnoreCase(target) && !entry.getKey().equals(currentToken)
        );
    }

    /**
     * Clears all active sessions and trackers (useful for tests).
     */
    public void clear() {
        sessions.clear();
        failureTrackers.clear();
    }
}
