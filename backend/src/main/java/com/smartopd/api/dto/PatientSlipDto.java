package com.smartopd.api.dto;

public record PatientSlipDto(
        String id,
        String token,
        String name,
        int age,
        String gender,
        String phone,
        String category,
        int severity,
        String doctorId,
        String doctorName,
        String department,
        String room,
        String appointmentTime,
        String registeredAt,
        String chiefComplaint,
        String status,
        boolean skipped,
        String consultationStartTime,
        String consultationEndTime,
        int position,
        long waitMinutes,
        String estimatedStart,
        String explanation
) {}
