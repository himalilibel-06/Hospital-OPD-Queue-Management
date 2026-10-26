package com.smartopd.api.dto;

public record CreateStaffRequest(String username, String displayName, String password, String role, String doctorId) {}
