package com.smartopd.api.dto;

public record CurrentUserResponse(
        String username,
        String role,
        String displayName,
        String doctorId
) {}
