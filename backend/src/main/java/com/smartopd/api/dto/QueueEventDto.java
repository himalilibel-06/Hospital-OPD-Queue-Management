package com.smartopd.api.dto;

public record QueueEventDto(
        String id,
        String timestamp,
        String tokenNumber,
        String doctorId,
        String type,
        String reason
) {}
