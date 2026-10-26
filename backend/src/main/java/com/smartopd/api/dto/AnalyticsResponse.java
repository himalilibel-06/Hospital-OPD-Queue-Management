package com.smartopd.api.dto;

import java.util.List;
import java.util.Map;

/**
 * AnalyticsResponse - Today's OPD operations, triage fairness, throughput,
 * and doctor utilization metrics matching the frontend dashboard.
 */
public record AnalyticsResponse(
        int totalPatients,
        Map<String, Integer> totalsByCategory,
        Map<Integer, Integer> severityDistribution,
        int waitingCount,
        int inConsultationCount,
        int completedCount,
        int noShowCount,
        int emergencyOverrideCount,
        int fairnessBoostCount,
        int averageWaitOverall,
        Map<String, Integer> averageWaitByCategory,
        LongestWaitDto longestWait,
        List<HourlyBucketDto> patientsPerHour,
        List<DoctorWaitDto> averageWaitPerDoctor,
        List<DoctorUtilisationDto> doctorUtilisation,
        int overallDoctorUtilisation
) {
    public record LongestWaitDto(
            long minutes,
            String tokenNumber,
            String patientName,
            String doctorName,
            String category
    ) {}

    public record HourlyBucketDto(
            String hour,
            String label,
            int count
    ) {}

    public record DoctorWaitDto(
            String doctorId,
            String doctorName,
            String department,
            String room,
            int avgWaitMinutes,
            int patientCount,
            int waitingCount
    ) {}

    public record DoctorUtilisationDto(
            String doctorId,
            String doctorName,
            String department,
            String room,
            String status,
            int utilisationPercent,
            int completedCount,
            int inConsultationCount,
            int waitingCount
    ) {}
}
