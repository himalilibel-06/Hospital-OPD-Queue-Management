package com.smartopd.api.dto;

import java.util.List;

/**
 * PublicBoardDoctorDto - Shape for the public live queue display board.
 * 
 * ============================================================================
 * Privacy Rule: Zero patient PII exposed (only tokens, doctor, room, and timings).
 * ============================================================================
 */
public record PublicBoardDoctorDto(
        String doctorId,
        String doctorName,
        String department,
        String room,
        String status,
        String nowServingToken,
        List<PublicTokenEstimateDto> nextTokens
) {}
