package com.smartopd.api.controller;

import com.smartopd.api.dto.CreateStaffRequest;
import com.smartopd.api.dto.StaffAccountDto;
import com.smartopd.api.dto.UpdatePasswordRequest;
import com.smartopd.api.security.RequiresRole;
import com.smartopd.api.security.SessionStore;
import com.smartopd.api.security.UserSession;
import com.smartopd.exception.ValidationException;
import com.smartopd.model.UserAccount;
import com.smartopd.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * StaffController - Front Desk staff account management endpoint.
 * 
 * ============================================================================
 * Security & RBAC:
 * - Restricted strictly to RECEPTIONIST role.
 * - GET /api/staff: Lists system staff accounts (never exposes passwords or hashes).
 * - POST /api/staff: Creates additional RECEPTIONIST or DOCTOR accounts.
 * - PUT /api/staff/{username}/password: Resets password for an existing account.
 * - DELETE /api/staff/{username}: Deletes a staff account and invalidates active sessions.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/staff")
@RequiresRole("RECEPTIONIST")
public class StaffController {

    private final QueueService queueService;
    private final SessionStore sessionStore;

    public StaffController(QueueService queueService, SessionStore sessionStore) {
        this.queueService = queueService;
        this.sessionStore = sessionStore;
    }

    @GetMapping
    public ResponseEntity<List<StaffAccountDto>> getStaffAccounts() {
        List<StaffAccountDto> accounts = queueService.getStaffAccounts().stream()
                .map(u -> new StaffAccountDto(u.getUsername(), u.getDisplayName(), u.getRole(), u.getDoctorId()))
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<StaffAccountDto> createStaffAccount(@RequestBody CreateStaffRequest request) {
        if (request == null) {
            throw new ValidationException("Request payload is required.");
        }

        UserAccount created = queueService.createStaffAccount(
                request.username(),
                request.displayName(),
                request.password(),
                request.role(),
                request.doctorId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new StaffAccountDto(created.getUsername(), created.getDisplayName(), created.getRole(), created.getDoctorId())
        );
    }

    @PutMapping("/{username}/password")
    public ResponseEntity<Map<String, String>> updatePassword(
            @PathVariable("username") String username,
            @RequestBody UpdatePasswordRequest request) {
        if (request == null || request.newPassword() == null) {
            throw new ValidationException("New password is required.");
        }

        queueService.updateStaffPassword(username, request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully for " + username + "."));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Map<String, String>> deleteStaffAccount(
            @PathVariable("username") String username,
            HttpServletRequest httpRequest) {
        UserSession session = (UserSession) httpRequest.getAttribute("currentUser");
        String requester = session != null ? session.username() : null;
        queueService.deleteStaffAccount(requester, username);
        sessionStore.invalidateSessionsForUser(username);
        return ResponseEntity.ok(Map.of("message", "Staff account '" + username + "' deleted successfully."));
    }
}
