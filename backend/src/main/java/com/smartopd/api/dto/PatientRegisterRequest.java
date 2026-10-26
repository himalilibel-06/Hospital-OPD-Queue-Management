package com.smartopd.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PatientRegisterRequest - Front desk registration payload with Bean Validation.
 */
public record PatientRegisterRequest(
        @NotBlank(message = "Patient name cannot be blank")
        String name,

        @Min(value = 0, message = "Age must be between 0 and 130")
        @Max(value = 130, message = "Age must be between 0 and 130")
        int age,

        String gender,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
        String phone,

        @NotBlank(message = "Category is required")
        @Pattern(regexp = "(?i)^(Emergency|Scheduled|Walk-in)$", message = "Category must be Emergency, Scheduled, or Walk-in")
        String category,

        @Min(value = 1, message = "Severity must be between 1 and 5")
        @Max(value = 5, message = "Severity must be between 1 and 5")
        int severity,

        @NotBlank(message = "Doctor ID is required")
        String doctorId,

        String appointmentTime,
        String chiefComplaint
) {}
