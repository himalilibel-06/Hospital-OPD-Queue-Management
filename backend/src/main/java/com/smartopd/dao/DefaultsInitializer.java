package com.smartopd.dao;

import com.smartopd.config.Settings;
import com.smartopd.dao.jdbc.JdbcSettingsDao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DefaultsInitializer - Initializes default hospital settings if the settings table is empty.
 * 
 * ============================================================================
 * OOP Concept: Clean Database State & Defensive Initialization
 * - Fresh database starts with 0 departments, 0 doctors, 0 patients, 0 users.
 * - Only seeds the baseline algorithmic scoring configuration if settings is empty.
 * - Token sequences are created on demand the first time a department and category
 *   combination is used (handled atomically by TokenSequenceDao).
 * ============================================================================
 */
public class DefaultsInitializer {

    private final ConnectionProvider connectionProvider;
    private final SettingsDao settingsDao = new JdbcSettingsDao();

    public DefaultsInitializer(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Initializes default hospital settings if the settings table is currently empty.
     * 
     * @return true if default settings were inserted, false if settings already existed
     */
    public boolean initializeIfEmpty() {
        try (Connection conn = connectionProvider.getConnection()) {
            if (!isSettingsEmpty(conn)) {
                return false;
            }

            TransactionHelper tx = new TransactionHelper(connectionProvider);
            return tx.execute(this::initialize);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check settings emptiness", e);
        }
    }

    public boolean isSettingsEmpty(Connection conn) {
        String sql = "SELECT COUNT(*) FROM settings";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            return true;
        }
    }

    public boolean initialize(Connection conn) {
        if (!isSettingsEmpty(conn)) {
            return false;
        }

        // Default Algorithm & Queue Triage Settings (100, 3, 150, 45, 45, cap 2)
        Settings defaultSettings = new Settings();
        defaultSettings.setSeverityWeight(100.0);
        defaultSettings.setWaitWeightPerMin(3.0);
        defaultSettings.setFairnessBoost(150.0);
        defaultSettings.setFairnessThresholdScheduled(45);
        defaultSettings.setFairnessThresholdWalkin(45);
        defaultSettings.setMaxConsecutiveEmergencies(2);
        settingsDao.save(conn, defaultSettings);

        return true;
    }
}
