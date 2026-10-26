package com.smartopd.dao;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;

/**
 * AppStateDao - Repository interface for application runtime state (clock offset, emergency counters).
 * 
 * ============================================================================
 * OOP Concept: State Encapsulation & Persistence
 * ============================================================================
 */
public interface AppStateDao {
    Optional<String> get(Connection conn, String key);
    void set(Connection conn, String key, String value);
    void remove(Connection conn, String key);
    Map<String, String> getAll(Connection conn);
}
