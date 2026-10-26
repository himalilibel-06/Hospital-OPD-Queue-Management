package com.smartopd.api.dto;

import java.util.List;

public record DoctorQueueOverviewDto(
        String doctorId,
        String doctorName,
        String department,
        String deptCode,
        String room,
        String status,
        int avgConsultMinutes,
        QueuePatientDto nowServing,
        List<QueuePatientDto> waitingList
) {}
