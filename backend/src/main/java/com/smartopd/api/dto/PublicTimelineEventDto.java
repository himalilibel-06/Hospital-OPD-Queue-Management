package com.smartopd.api.dto;

public record PublicTimelineEventDto(
        String timestamp,
        String type,
        String reason
) {}
