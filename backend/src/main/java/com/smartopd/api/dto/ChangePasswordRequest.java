package com.smartopd.api.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
