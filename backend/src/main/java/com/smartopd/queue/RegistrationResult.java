package com.smartopd.queue;

import com.smartopd.model.Patient;
import com.smartopd.model.Token;

/**
 * RegistrationResult - Encapsulates the complete outcome of registering a patient.
 * 
 * ============================================================================
 * OOP Concept: Encapsulation & Response Object Pattern
 * ============================================================================
 */
public class RegistrationResult {
    private final Patient patient;
    private final Token token;
    private final int position;
    private final int estimatedWaitMinutes;
    private final String estimatedTimeStr;
    private final String explanation;

    public RegistrationResult(Patient patient, int position, int estimatedWaitMinutes,
                              String estimatedTimeStr, String explanation) {
        this.patient = patient;
        this.token = patient != null ? patient.getToken() : null;
        this.position = position;
        this.estimatedWaitMinutes = estimatedWaitMinutes;
        this.estimatedTimeStr = estimatedTimeStr;
        this.explanation = explanation;
    }

    public Patient getPatient() {
        return patient;
    }

    public Token getToken() {
        return token;
    }

    public int getPosition() {
        return position;
    }

    public int getEstimatedWaitMinutes() {
        return estimatedWaitMinutes;
    }

    public String getEstimatedTimeStr() {
        return estimatedTimeStr;
    }

    public String getExplanation() {
        return explanation;
    }
}
