package com.smartopd.dao;

import com.smartopd.config.Settings;
import java.sql.Connection;

/**
 * SettingsDao - Repository interface for hospital scoring weights and triage parameters.
 * 
 * ============================================================================
 * OOP Concept: Configuration State Persistence
 * ============================================================================
 */
public interface SettingsDao {
    Settings load(Connection conn);
    void save(Connection conn, Settings settings);
}
