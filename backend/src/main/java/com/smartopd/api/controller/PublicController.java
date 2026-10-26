package com.smartopd.api.controller;

import com.smartopd.api.dto.PublicBoardDoctorDto;
import com.smartopd.api.dto.PublicTrackerResponse;
import com.smartopd.api.security.PublicTrackerRateLimiter;
import com.smartopd.api.security.RateLimitExceededException;
import com.smartopd.exception.ResourceNotFoundException;
import com.smartopd.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PublicController - Public, unauthenticated endpoints for display board & patient self-service tracker.
 * 
 * ============================================================================
 * Privacy by Design Rule:
 * - Publicly exposes ONLY anonymous tokens, doctor room numbers, and estimated times.
 * - Zero patient PII: NEVER exposes full name, age, phone number, severity number, or medical complaint.
 * - Tracker greeting permits first name ONLY for personal identification.
 * - Rate Limiting: 30 requests per minute per IP on /api/public/tracker/** to defend
 *   against enumeration of sequential tokens (CARD-W-006).
 * ============================================================================
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final QueueService queueService;
    private final PublicTrackerRateLimiter rateLimiter;

    public PublicController(QueueService queueService, PublicTrackerRateLimiter rateLimiter) {
        this.queueService = queueService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Public OPD Live Display Board data across all clinical departments.
     */
    @GetMapping("/board")
    public ResponseEntity<List<PublicBoardDoctorDto>> getPublicBoard() {
        return ResponseEntity.ok(queueService.getPublicBoardData());
    }

    /**
     * Live personal tracker summary and timeline for a given token number.
     * Enforces rate limit of 30 req/min per IP address.
     */
    @GetMapping("/tracker/{token}")
    public ResponseEntity<PublicTrackerResponse> getPublicTracker(
            @PathVariable("token") String token,
            HttpServletRequest request
    ) {
        String clientIp = resolveClientIp(request);
        if (!rateLimiter.isAllowed(clientIp)) {
            throw new RateLimitExceededException("Rate limit exceeded for IP " + clientIp + ": Maximum 30 requests per minute on public tracker. Please try again later.");
        }

        PublicTrackerResponse summary = queueService.getPublicQueueSummary(token);
        if (summary == null) {
            throw new ResourceNotFoundException("Token '" + token + "' was not found in today's OPD registry. Please check your token slip.");
        }
        return ResponseEntity.ok(summary);
    }

    /**
     * Up to 5 currently waiting sample tokens for demo "Try a Sample" chips.
     */
    @GetMapping("/sample-tokens")
    public ResponseEntity<List<String>> getSampleTokens() {
        return ResponseEntity.ok(queueService.getSampleTokens());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "127.0.0.1";
    }
}
