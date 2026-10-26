package com.smartopd.api.dto;

import java.util.List;

/**
 * PublicTrackerResponse - Shape for the patient self-service live tracker.
 * 
 * ============================================================================
 * Privacy Rule:
 * Exposes first name ONLY for greeting. Never full name, phone number,
 * age, medical chief complaint, or severity level.
 * ============================================================================
 */
public record PublicTrackerResponse(
        String token,
        String firstName,
        String doctor,
        String department,
        String room,
        String category,
        String status,
        int position,
        int patientsAhead,
        String estimatedStart,
        long estimatedWaitMinutes,
        String explanation,
        boolean fairnessBoost,
        boolean isNext,
        boolean isEmergencyDelayed,
        List<PublicTimelineEventDto> timeline
) {}
