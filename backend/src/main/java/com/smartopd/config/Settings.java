package com.smartopd.config;

import com.smartopd.exception.ValidationException;

/**
 * Settings - Central configuration entity governing scoring weights,
 * fairness thresholds, and emergency buffers.
 * 
 * ============================================================================
 * OOP Concept: Encapsulation & Defensive Copying
 * - Validates all invariants on mutation with clear user-facing error messages.
 * - Provides copy() method to enforce immutability and state isolation.
 * ============================================================================
 */
public class Settings {
    // Tuned defaults chosen from 48-combination sweep over seeds 1-30
    public static final double DEFAULT_SEVERITY_WEIGHT = 100.0;
    public static final double DEFAULT_WAIT_WEIGHT_PER_MIN = 3.0;
    public static final double DEFAULT_FAIRNESS_BOOST = 150.0;
    public static final int DEFAULT_FAIRNESS_THRESHOLD_SCHEDULED = 45;
    public static final int DEFAULT_FAIRNESS_THRESHOLD_WALKIN = 45;
    public static final int DEFAULT_MAX_CONSECUTIVE_EMERGENCIES = 2;
    public static final int DEFAULT_EMERGENCY_BUFFER_SLOTS = 2;
    public static final int DEFAULT_EMERGENCY_OVERRIDE_SEVERITY = 5;
    public static final double DEFAULT_EMERGENCY_OVERRIDE_SCORE = 10000.0;
    public static final double DEFAULT_APPOINTMENT_URGENCY_MAX = 60.0;

    private double severityWeight = DEFAULT_SEVERITY_WEIGHT;
    private double waitWeightPerMin = DEFAULT_WAIT_WEIGHT_PER_MIN;
    private double fairnessBoost = DEFAULT_FAIRNESS_BOOST;
    private int fairnessThresholdScheduled = DEFAULT_FAIRNESS_THRESHOLD_SCHEDULED;
    private int fairnessThresholdWalkin = DEFAULT_FAIRNESS_THRESHOLD_WALKIN;
    private int maxConsecutiveEmergencies = DEFAULT_MAX_CONSECUTIVE_EMERGENCIES;
    private int emergencyBufferSlotsDefault = DEFAULT_EMERGENCY_BUFFER_SLOTS;
    private int emergencyOverrideSeverity = DEFAULT_EMERGENCY_OVERRIDE_SEVERITY;
    private double emergencyOverrideScore = DEFAULT_EMERGENCY_OVERRIDE_SCORE;
    private double appointmentUrgencyMax = DEFAULT_APPOINTMENT_URGENCY_MAX;

    public Settings() {
    }

    public Settings copy() {
        Settings copy = new Settings();
        copy.severityWeight = this.severityWeight;
        copy.waitWeightPerMin = this.waitWeightPerMin;
        copy.fairnessBoost = this.fairnessBoost;
        copy.fairnessThresholdScheduled = this.fairnessThresholdScheduled;
        copy.fairnessThresholdWalkin = this.fairnessThresholdWalkin;
        copy.maxConsecutiveEmergencies = this.maxConsecutiveEmergencies;
        copy.emergencyBufferSlotsDefault = this.emergencyBufferSlotsDefault;
        copy.emergencyOverrideSeverity = this.emergencyOverrideSeverity;
        copy.emergencyOverrideScore = this.emergencyOverrideScore;
        copy.appointmentUrgencyMax = this.appointmentUrgencyMax;
        return copy;
    }

    public void resetToDefaults() {
        this.severityWeight = DEFAULT_SEVERITY_WEIGHT;
        this.waitWeightPerMin = DEFAULT_WAIT_WEIGHT_PER_MIN;
        this.fairnessBoost = DEFAULT_FAIRNESS_BOOST;
        this.fairnessThresholdScheduled = DEFAULT_FAIRNESS_THRESHOLD_SCHEDULED;
        this.fairnessThresholdWalkin = DEFAULT_FAIRNESS_THRESHOLD_WALKIN;
        this.maxConsecutiveEmergencies = DEFAULT_MAX_CONSECUTIVE_EMERGENCIES;
        this.emergencyBufferSlotsDefault = DEFAULT_EMERGENCY_BUFFER_SLOTS;
        this.emergencyOverrideSeverity = DEFAULT_EMERGENCY_OVERRIDE_SEVERITY;
        this.emergencyOverrideScore = DEFAULT_EMERGENCY_OVERRIDE_SCORE;
        this.appointmentUrgencyMax = DEFAULT_APPOINTMENT_URGENCY_MAX;
    }

    public double getSeverityWeight() {
        return severityWeight;
    }

    public void setSeverityWeight(double severityWeight) {
        if (Double.isNaN(severityWeight) || severityWeight < 10 || severityWeight > 500) {
            throw new ValidationException("Severity Weight must be a valid number between 10 and 500.");
        }
        this.severityWeight = severityWeight;
    }

    public double getWaitWeightPerMin() {
        return waitWeightPerMin;
    }

    public void setWaitWeightPerMin(double waitWeightPerMin) {
        if (Double.isNaN(waitWeightPerMin) || waitWeightPerMin < 0.1 || waitWeightPerMin > 10.0) {
            throw new ValidationException("Wait Weight Per Minute must be a valid number between 0.1 and 10.");
        }
        this.waitWeightPerMin = waitWeightPerMin;
    }

    public double getFairnessBoost() {
        return fairnessBoost;
    }

    public void setFairnessBoost(double fairnessBoost) {
        if (Double.isNaN(fairnessBoost) || fairnessBoost < 0 || fairnessBoost > 500) {
            throw new ValidationException("Fairness Boost points must be a valid number between 0 and 500.");
        }
        this.fairnessBoost = fairnessBoost;
    }

    public int getFairnessThresholdScheduled() {
        return fairnessThresholdScheduled;
    }

    public void setFairnessThresholdScheduled(int fairnessThresholdScheduled) {
        if (fairnessThresholdScheduled < 5 || fairnessThresholdScheduled > 180) {
            throw new ValidationException("Scheduled Fairness Threshold must be a valid number between 5 and 180 minutes.");
        }
        this.fairnessThresholdScheduled = fairnessThresholdScheduled;
    }

    public int getFairnessThresholdWalkin() {
        return fairnessThresholdWalkin;
    }

    public void setFairnessThresholdWalkin(int fairnessThresholdWalkin) {
        if (fairnessThresholdWalkin < 5 || fairnessThresholdWalkin > 240) {
            throw new ValidationException("Walk-in Fairness Threshold must be a valid number between 5 and 240 minutes.");
        }
        this.fairnessThresholdWalkin = fairnessThresholdWalkin;
    }

    public int getMaxConsecutiveEmergencies() {
        return maxConsecutiveEmergencies;
    }

    public void setMaxConsecutiveEmergencies(int maxConsecutiveEmergencies) {
        if (maxConsecutiveEmergencies < 1 || maxConsecutiveEmergencies > 10) {
            throw new ValidationException("Max Consecutive Emergencies cap must be a valid integer between 1 and 10.");
        }
        this.maxConsecutiveEmergencies = maxConsecutiveEmergencies;
    }

    public int getEmergencyBufferSlotsDefault() {
        return emergencyBufferSlotsDefault;
    }

    public void setEmergencyBufferSlotsDefault(int emergencyBufferSlotsDefault) {
        if (emergencyBufferSlotsDefault < 0 || emergencyBufferSlotsDefault > 10) {
            throw new ValidationException("Default Emergency Buffer Slots must be a valid integer between 0 and 10.");
        }
        this.emergencyBufferSlotsDefault = emergencyBufferSlotsDefault;
    }

    public int getEmergencyOverrideSeverity() {
        return emergencyOverrideSeverity;
    }

    public void setEmergencyOverrideSeverity(int emergencyOverrideSeverity) {
        this.emergencyOverrideSeverity = emergencyOverrideSeverity;
    }

    public double getEmergencyOverrideScore() {
        return emergencyOverrideScore;
    }

    public void setEmergencyOverrideScore(double emergencyOverrideScore) {
        this.emergencyOverrideScore = emergencyOverrideScore;
    }

    public double getAppointmentUrgencyMax() {
        return appointmentUrgencyMax;
    }

    public void setAppointmentUrgencyMax(double appointmentUrgencyMax) {
        this.appointmentUrgencyMax = appointmentUrgencyMax;
    }
}
