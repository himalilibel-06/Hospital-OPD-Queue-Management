package com.smartopd.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DepartmentDto(
        @NotBlank(message = "Department code is required") String code,
        @NotBlank(message = "Department name is required") String name
) {}
