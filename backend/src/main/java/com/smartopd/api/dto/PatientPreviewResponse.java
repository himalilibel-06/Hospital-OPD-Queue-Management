package com.smartopd.api.dto;

public record PatientPreviewResponse(
        String predictedToken,
        int position,
        long estimatedWaitMinutes,
        String estimatedStart,
        String explanation,
        String doctorId,
        String doctorName
) {}
