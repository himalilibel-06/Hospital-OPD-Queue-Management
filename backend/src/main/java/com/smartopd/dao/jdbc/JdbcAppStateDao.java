package com.smartopd.dao.jdbc;

import com.smartopd.dao.AppStateDao;
import com.smartopd.dao.DataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JdbcAppStateDao - JDBC implementation of AppStateDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & SQL Parameter Binding
 * ============================================================================
 */
public class JdbcAppStateDao implements AppStateDao {

    @Override
    public Optional<String> get(Connection conn, String key) {
        String sql = "SELECT value FROM app_state WHERE key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("value"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get app_state for key: " + key, e);
        }
    }

    @Override
    public void set(Connection conn, String key, String value) {
        String sql = "INSERT INTO app_state (key, value) VALUES (?, ?) "
                   + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set app_state for key: " + key, e);
        }
    }

    @Override
    public void remove(Connection conn, String key) {
        String sql = "DELETE FROM app_state WHERE key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to remove app_state for key: " + key, e);
        }
    }

    @Override
    public Map<String, String> getAll(Connection conn) {
        String sql = "SELECT key, value FROM app_state";
        Map<String, String> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("key"), rs.getString("value"));
            }
            return map;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get all app_state entries", e);
        }
    }
}
