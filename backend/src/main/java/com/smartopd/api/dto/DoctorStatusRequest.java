package com.smartopd.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DoctorStatusRequest(
        @NotBlank(message = "Status cannot be blank") String status
) {}
