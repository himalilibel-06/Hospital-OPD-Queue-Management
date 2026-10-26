package com.smartopd.api.dto;

public record PublicTokenEstimateDto(
        String token,
        String category,
        boolean isEmergency,
        String estimatedStart
) {}
