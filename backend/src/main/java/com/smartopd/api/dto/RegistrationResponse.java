package com.smartopd.api.dto;

public record RegistrationResponse(
        String token,
        int position,
        long estimatedWaitMinutes,
        String estimatedStart,
        String explanation,
        String doctorId,
        String doctorName,
        String department,
        String room
) {}
