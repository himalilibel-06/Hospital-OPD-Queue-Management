package com.smartopd.queue;

/**
 * WaitEstimate - Encapsulates estimated consultation start time and queue position.
 * 
 * ============================================================================
 * OOP Concept: Value Object Pattern
 * ============================================================================
 */
public class WaitEstimate {
    private final boolean paused;
    private final String estimatedTimeStr;
    private final int waitMinutes;
    private final int position;
    private final int patientsAhead;

    public WaitEstimate(boolean paused, String estimatedTimeStr, int waitMinutes, int position, int patientsAhead) {
        this.paused = paused;
        this.estimatedTimeStr = estimatedTimeStr;
        this.waitMinutes = waitMinutes;
        this.position = position;
        this.patientsAhead = patientsAhead;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getEstimatedTimeStr() {
        return estimatedTimeStr;
    }

    public int getWaitMinutes() {
        return waitMinutes;
    }

    public int getPosition() {
        return position;
    }

    public int getPatientsAhead() {
        return patientsAhead;
    }
}
