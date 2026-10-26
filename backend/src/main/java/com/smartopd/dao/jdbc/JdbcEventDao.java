package com.smartopd.dao.jdbc;

import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.EventDao;
import com.smartopd.events.QueueEvent;
import com.smartopd.model.EventType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JdbcEventDao - JDBC implementation of EventDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & Parameterized SQL
 * - Stores immutable QueueEvents and queries timelines.
 * ============================================================================
 */
public class JdbcEventDao implements EventDao {

    @Override
    public void append(Connection conn, QueueEvent event) {
        // SQL injection defense: Parameter binding
        String sql = "INSERT INTO queue_event (timestamp, token, doctor_id, type, reason) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getTimestamp().toString());
            ps.setString(2, event.getTokenNumber());
            ps.setString(3, event.getDoctorId());
            ps.setString(4, event.getType().name());
            ps.setString(5, event.getReason());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to append queue event for token: " + event.getTokenNumber(), e);
        }
    }

    @Override
    public List<QueueEvent> findByDoctor(Connection conn, String doctorId, int limit) {
        String sql = "SELECT id, timestamp, token, doctor_id, type, reason FROM queue_event "
                   + "WHERE doctor_id = ? ORDER BY id DESC LIMIT ?";
        List<QueueEvent> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, doctorId);
            ps.setInt(2, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEvent(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query events for doctor: " + doctorId, e);
        }
    }

    @Override
    public List<QueueEvent> findByToken(Connection conn, String token, int limit) {
        // Oldest-first for a token timeline as specified
        String sql = "SELECT id, timestamp, token, doctor_id, type, reason FROM queue_event "
                   + "WHERE UPPER(token) = UPPER(?) ORDER BY id ASC LIMIT ?";
        List<QueueEvent> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token != null ? token.trim() : "");
            ps.setInt(2, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEvent(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query event timeline for token: " + token, e);
        }
    }

    @Override
    public List<QueueEvent> findAll(Connection conn, int limit) {
        String sql = "SELECT id, timestamp, token, doctor_id, type, reason FROM queue_event "
                   + "ORDER BY id DESC LIMIT ?";
        List<QueueEvent> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEvent(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query all events", e);
        }
    }

    private QueueEvent mapEvent(ResultSet rs) throws SQLException {
        String id = "EVT-" + rs.getInt("id");
        Instant timestamp = Instant.parse(rs.getString("timestamp"));
        String typeStr = rs.getString("type");
        EventType type;
        try {
            type = EventType.valueOf(typeStr);
        } catch (Exception e) {
            type = EventType.REGISTERED;
        }

        return new QueueEvent(
                id,
                timestamp,
                type,
                rs.getString("token"),
                rs.getString("doctor_id"),
                rs.getString("reason")
        );
    }
}
