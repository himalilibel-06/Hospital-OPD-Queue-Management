package com.smartopd.api.dto;

public record ClockResponse(
        String currentTime,
        long offsetMinutes
) {}
