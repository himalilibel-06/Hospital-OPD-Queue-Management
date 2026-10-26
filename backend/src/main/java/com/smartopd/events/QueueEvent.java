package com.smartopd.events;

import com.smartopd.model.EventType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * QueueEvent - Immutable audit record capturing a state transition or queue shift.
 * 
 * ============================================================================
 * OOP Concept: Immutable Event Model & Encapsulation
 * ============================================================================
 */
public final class QueueEvent {
    private final String id;
    private final Instant timestamp;
    private final EventType type;
    private final String tokenNumber;
    private final String doctorId;
    private final String reason;

    public QueueEvent(String id, Instant timestamp, EventType type, String tokenNumber, String doctorId, String reason) {
        this.id = id != null ? id : "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.type = type != null ? type : EventType.REGISTERED;
        this.tokenNumber = tokenNumber != null ? tokenNumber : "SYSTEM";
        this.doctorId = doctorId != null ? doctorId : "SYSTEM";
        this.reason = reason != null ? reason : "";
    }

    public QueueEvent(EventType type, String tokenNumber, String doctorId, String reason, Instant now) {
        this("EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), now, type, tokenNumber, doctorId, reason);
    }

    public String getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public EventType getType() {
        return type;
    }

    public String getTokenNumber() {
        return tokenNumber;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s): %s", timestamp, type, tokenNumber, reason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueEvent that = (QueueEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
