package com.smartopd.dao.jdbc;

import com.smartopd.config.Settings;
import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.SettingsDao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * JdbcSettingsDao - JDBC implementation of SettingsDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & Key-Value Persistence
 * - Uses PreparedStatements to read and save Settings configuration.
 * ============================================================================
 */
public class JdbcSettingsDao implements SettingsDao {

    @Override
    public Settings load(Connection conn) {
        String sql = "SELECT key, value FROM settings";
        Map<String, String> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("key"), rs.getString("value"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load settings from database", e);
        }

        if (map.isEmpty()) {
            return new Settings();
        }

        Settings settings = new Settings();
        if (map.containsKey("SEVERITY_WEIGHT")) {
            settings.setSeverityWeight(Double.parseDouble(map.get("SEVERITY_WEIGHT")));
        }
        if (map.containsKey("WAIT_WEIGHT_PER_MIN")) {
            settings.setWaitWeightPerMin(Double.parseDouble(map.get("WAIT_WEIGHT_PER_MIN")));
        }
        if (map.containsKey("FAIRNESS_BOOST")) {
            settings.setFairnessBoost(Double.parseDouble(map.get("FAIRNESS_BOOST")));
        }
        if (map.containsKey("FAIRNESS_THRESHOLD_SCHEDULED")) {
            settings.setFairnessThresholdScheduled(Integer.parseInt(map.get("FAIRNESS_THRESHOLD_SCHEDULED")));
        }
        if (map.containsKey("FAIRNESS_THRESHOLD_WALKIN")) {
            settings.setFairnessThresholdWalkin(Integer.parseInt(map.get("FAIRNESS_THRESHOLD_WALKIN")));
        }
        if (map.containsKey("MAX_CONSECUTIVE_EMERGENCIES")) {
            settings.setMaxConsecutiveEmergencies(Integer.parseInt(map.get("MAX_CONSECUTIVE_EMERGENCIES")));
        }
        if (map.containsKey("EMERGENCY_BUFFER_SLOTS_DEFAULT")) {
            settings.setEmergencyBufferSlotsDefault(Integer.parseInt(map.get("EMERGENCY_BUFFER_SLOTS_DEFAULT")));
        }
        if (map.containsKey("EMERGENCY_OVERRIDE_SEVERITY")) {
            settings.setEmergencyOverrideSeverity(Integer.parseInt(map.get("EMERGENCY_OVERRIDE_SEVERITY")));
        }
        if (map.containsKey("EMERGENCY_OVERRIDE_SCORE")) {
            settings.setEmergencyOverrideScore(Double.parseDouble(map.get("EMERGENCY_OVERRIDE_SCORE")));
        }
        if (map.containsKey("APPOINTMENT_URGENCY_MAX")) {
            settings.setAppointmentUrgencyMax(Double.parseDouble(map.get("APPOINTMENT_URGENCY_MAX")));
        }
        return settings;
    }

    @Override
    public void save(Connection conn, Settings settings) {
        if (settings == null) return;
        Map<String, String> map = new HashMap<>();
        map.put("SEVERITY_WEIGHT", String.valueOf(settings.getSeverityWeight()));
        map.put("WAIT_WEIGHT_PER_MIN", String.valueOf(settings.getWaitWeightPerMin()));
        map.put("FAIRNESS_BOOST", String.valueOf(settings.getFairnessBoost()));
        map.put("FAIRNESS_THRESHOLD_SCHEDULED", String.valueOf(settings.getFairnessThresholdScheduled()));
        map.put("FAIRNESS_THRESHOLD_WALKIN", String.valueOf(settings.getFairnessThresholdWalkin()));
        map.put("MAX_CONSECUTIVE_EMERGENCIES", String.valueOf(settings.getMaxConsecutiveEmergencies()));
        map.put("EMERGENCY_BUFFER_SLOTS_DEFAULT", String.valueOf(settings.getEmergencyBufferSlotsDefault()));
        map.put("EMERGENCY_OVERRIDE_SEVERITY", String.valueOf(settings.getEmergencyOverrideSeverity()));
        map.put("EMERGENCY_OVERRIDE_SCORE", String.valueOf(settings.getEmergencyOverrideScore()));
        map.put("APPOINTMENT_URGENCY_MAX", String.valueOf(settings.getAppointmentUrgencyMax()));

        String sql = "INSERT INTO settings (key, value) VALUES (?, ?) "
                   + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                ps.setString(1, entry.getKey());
                ps.setString(2, entry.getValue());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save settings", e);
        }
    }
}
