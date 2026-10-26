package com.smartopd.api.dto;

public record SettingsDto(
        double severityWeight,
        double waitWeightPerMin,
        double fairnessBoost,
        int fairnessThresholdScheduled,
        int fairnessThresholdWalkin,
        int maxConsecutiveEmergencies,
        int emergencyBufferSlotsDefault
) {}
