package com.smartopd.dao;

import com.smartopd.events.QueueEvent;
import java.sql.Connection;
import java.util.List;

/**
 * EventDao - Repository interface for QueueEvent audit trail.
 * 
 * ============================================================================
 * OOP Concept: Event Sourcing & Audit Log Decoupling
 * ============================================================================
 */
public interface EventDao {
    void append(Connection conn, QueueEvent event);
    List<QueueEvent> findByDoctor(Connection conn, String doctorId, int limit);
    List<QueueEvent> findByToken(Connection conn, String token, int limit);
    List<QueueEvent> findAll(Connection conn, int limit);
}
