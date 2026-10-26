package com.smartopd.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EventLog - Audit log storage maintaining recent queue lifecycle transitions.
 * 
 * ============================================================================
 * OOP Concept: Encapsulation & Query Object Pattern
 * - Maintains a bounded sliding window of the last 100 queue events.
 * ============================================================================
 */
public class EventLog {
    private static final int MAX_CAPACITY = 100;
    private final List<QueueEvent> events = new ArrayList<>();

    public synchronized void log(QueueEvent event) {
        if (event == null) return;
        // Prepend to match frontend unshift behavior (newest first)
        events.add(0, event);
        if (events.size() > MAX_CAPACITY) {
            events.remove(events.size() - 1);
        }
    }

    public synchronized List<QueueEvent> getEvents(String doctorId, String tokenNumber, int limit) {
        return events.stream()
                .filter(e -> {
                    if (doctorId != null && !doctorId.equalsIgnoreCase("All")) {
                        return doctorId.equalsIgnoreCase(e.getDoctorId());
                    }
                    return true;
                })
                .filter(e -> {
                    if (tokenNumber != null && !tokenNumber.trim().isEmpty()) {
                        return e.getTokenNumber().toLowerCase().contains(tokenNumber.trim().toLowerCase());
                    }
                    return true;
                })
                .limit(limit > 0 ? limit : 25)
                .collect(Collectors.toList());
    }

    public synchronized List<QueueEvent> getAllEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized void clear() {
        events.clear();
    }
}
