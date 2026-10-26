package com.smartopd.api.controller;

import com.smartopd.api.dto.SetupRequest;
import com.smartopd.api.dto.SetupStatusResponse;
import com.smartopd.api.security.RateLimitExceededException;
import com.smartopd.api.security.SetupRateLimiter;
import com.smartopd.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SetupController - Public initial first-run setup endpoint.
 * 
 * ============================================================================
 * Security & Setup:
 * - GET /api/setup/status is public and reports whether any user accounts exist.
 * - POST /api/setup creates the first RECEPTIONIST account only while database has 0 users.
 * - Rate-limited to 10 requests per minute per client IP.
 * - Once setup is complete, subsequent POST /api/setup requests return 409 Conflict.
 * - Does not return a session token; the user signs in via standard login.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final QueueService queueService;
    private final SetupRateLimiter setupRateLimiter;

    public SetupController(QueueService queueService, SetupRateLimiter setupRateLimiter) {
        this.queueService = queueService;
        this.setupRateLimiter = setupRateLimiter;
    }

    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> getSetupStatus() {
        boolean needsSetup = queueService.needsSetup();
        return ResponseEntity.ok(new SetupStatusResponse(needsSetup));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> completeSetup(@RequestBody SetupRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        if (!setupRateLimiter.isAllowed(clientIp)) {
            throw new RateLimitExceededException("Too many setup attempts. Maximum 10 requests per minute per IP.");
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request payload is required."));
        }

        queueService.completeSetup(request.username(), request.displayName(), request.password());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Initial setup completed successfully. Please sign in with your receptionist credentials."
        ));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
