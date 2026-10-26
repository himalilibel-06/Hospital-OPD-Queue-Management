package com.smartopd.api.dto;

public record LoginResponse(
        String token,
        String role,
        String displayName,
        String doctorId
) {}
