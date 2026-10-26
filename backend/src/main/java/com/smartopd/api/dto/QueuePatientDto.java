package com.smartopd.api.dto;

public record QueuePatientDto(
        String id,
        String token,
        String name,
        int age,
        String gender,
        String phone,
        String category,
        int severity,
        String registeredAt,
        long waitMinutes,
        double score,
        boolean fairnessBoosted,
        boolean isEmergency,
        int position,
        String estimatedStart,
        String explanation,
        String appointmentTime,
        String chiefComplaint,
        String status,
        boolean skipped
) {}
