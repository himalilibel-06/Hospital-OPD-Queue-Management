package com.smartopd.api.controller;

import com.smartopd.api.dto.CurrentUserResponse;
import com.smartopd.api.dto.LoginRequest;
import com.smartopd.api.dto.LoginResponse;
import com.smartopd.api.security.SessionStore;
import com.smartopd.api.security.UserSession;
import com.smartopd.service.AuthResult;
import com.smartopd.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * AuthController - Handles user authentication, token sessions, and identity introspection.
 * 
 * ============================================================================
 * OOP & Security Concepts:
 * - Hand-written authentication: Explainable PBKDF2 hash verification via QueueService.
 * - Brute-Force Defense: Enforces a 5-failures/5-minutes lockout policy.
 * - Stateless Session Tokens: Generates 256-bit entropy tokens stored in memory.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final QueueService queueService;
    private final SessionStore sessionStore;

    public AuthController(QueueService queueService, SessionStore sessionStore) {
        this.queueService = queueService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // 1. Check for active brute-force lockout
        sessionStore.checkLockout(request.username());

        // 2. Authenticate credentials against PBKDF2 database hash
        Optional<AuthResult> authResult = queueService.authenticate(request.username(), request.password());
        if (authResult.isEmpty()) {
            // Record failure (triggers AccountLockedException if threshold exceeded)
            sessionStore.recordFailedAttempt(request.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Invalid username or password."
            ));
        }

        // 3. Clear failure tracking and issue 256-bit bearer token valid for 8 hours
        sessionStore.recordSuccess(request.username());
        AuthResult auth = authResult.get();
        UserSession session = sessionStore.createSession(auth.username(), auth.role(), auth.displayName(), auth.doctorId());

        return ResponseEntity.ok(new LoginResponse(
                session.token(),
                session.role(),
                session.displayName(),
                session.doctorId()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            sessionStore.invalidateSession(token);
        }
        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Logged out successfully."
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        UserSession session = (UserSession) request.getAttribute("currentUser");
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Not authenticated."
            ));
        }
        return ResponseEntity.ok(new CurrentUserResponse(
                session.username(),
                session.role(),
                session.displayName(),
                session.doctorId()
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody com.smartopd.api.dto.ChangePasswordRequest request, HttpServletRequest httpRequest) {
        UserSession session = (UserSession) httpRequest.getAttribute("currentUser");
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Not authenticated."
            ));
        }

        if (request == null || request.currentPassword() == null || request.newPassword() == null) {
            throw new com.smartopd.exception.ValidationException("Current password and new password are required.");
        }

        String username = session.username();
        String currentToken = session.token();

        // 1. Check for active brute-force lockout
        sessionStore.checkLockout(username);

        // 2. Verify current password
        boolean verified = queueService.verifyCurrentPassword(username, request.currentPassword());
        if (!verified) {
            sessionStore.recordFailedAttempt(username);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", "Current password is incorrect."
            ));
        }

        // 3. Clear failed attempts on valid password check
        sessionStore.recordSuccess(username);

        // 4. Update password (validates new password rules: >=8 chars, letter + digit)
        queueService.updateUserPassword(username, request.newPassword());

        // 5. Invalidate OTHER sessions for this user, but keep current session
        sessionStore.invalidateOtherSessionsForUser(username, currentToken);

        return ResponseEntity.ok(Map.of(
                "status", 200,
                "message", "Password changed successfully."
        ));
    }
}
