package com.smartopd.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DoctorDto(
        String id,
        @NotBlank(message = "Doctor name is required") String name,
        @Min(value = 20, message = "Age must be at least 20") @Max(value = 100, message = "Age must be at most 100") int age,
        String gender,
        String phone,
        @NotBlank(message = "Department is required") String department,
        @NotBlank(message = "Department code is required") String deptCode,
        @NotBlank(message = "Room is required") String room,
        @NotBlank(message = "Working hours are required") String workingHours,
        @Min(value = 1, message = "Average consultation minutes must be positive") int avgConsultMinutes,
        @Min(value = 0, message = "Emergency buffer slots must be non-negative") int emergencyBufferSlots,
        String status
) {}
