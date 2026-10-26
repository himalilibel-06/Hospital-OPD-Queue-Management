package com.smartopd.dao.jdbc;

import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.DoctorDao;
import com.smartopd.model.Doctor;
import com.smartopd.model.DoctorStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcDoctorDao - JDBC implementation of DoctorDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & SQL Injection Defense
 * - ONLY PreparedStatement with bound parameters is used.
 * ============================================================================
 */
public class JdbcDoctorDao implements DoctorDao {

    @Override
    public void insert(Connection conn, Doctor doctor) {
        // SQL injection defense: PreparedStatement parameter binding
        String sql = "INSERT INTO doctor (id, name, department_code, room, start_time, end_time, "
                   + "avg_consult_minutes, emergency_buffer_slots, status, age, gender, phone) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String startTime = "09:00";
            String endTime = "17:00";
            String wh = doctor.getWorkingHours();
            if (wh != null && wh.contains("-")) {
                String[] parts = wh.split("-");
                if (parts.length >= 2) {
                    startTime = parts[0].trim();
                    endTime = parts[1].trim();
                }
            }

            ps.setString(1, doctor.getId());
            ps.setString(2, doctor.getName());
            ps.setString(3, doctor.getDeptCode());
            ps.setString(4, doctor.getRoom());
            ps.setString(5, startTime);
            ps.setString(6, endTime);
            ps.setInt(7, doctor.getAvgConsultMinutes());
            ps.setInt(8, doctor.getEmergencyBufferSlots());
            ps.setString(9, doctor.getStatus().name());
            ps.setInt(10, doctor.getAge());
            ps.setString(11, doctor.getGender());
            ps.setString(12, doctor.getPhone());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert doctor: " + doctor.getId(), e);
        }
    }

    @Override
    public void update(Connection conn, Doctor doctor) {
        // SQL injection defense: Parameterized update
        String sql = "UPDATE doctor SET name = ?, department_code = ?, room = ?, start_time = ?, end_time = ?, "
                   + "avg_consult_minutes = ?, emergency_buffer_slots = ?, status = ?, age = ?, gender = ?, phone = ? "
                   + "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String startTime = "09:00";
            String endTime = "17:00";
            String wh = doctor.getWorkingHours();
            if (wh != null && wh.contains("-")) {
                String[] parts = wh.split("-");
                if (parts.length >= 2) {
                    startTime = parts[0].trim();
                    endTime = parts[1].trim();
                }
            }

            ps.setString(1, doctor.getName());
            ps.setString(2, doctor.getDeptCode());
            ps.setString(3, doctor.getRoom());
            ps.setString(4, startTime);
            ps.setString(5, endTime);
            ps.setInt(6, doctor.getAvgConsultMinutes());
            ps.setInt(7, doctor.getEmergencyBufferSlots());
            ps.setString(8, doctor.getStatus().name());
            ps.setInt(9, doctor.getAge());
            ps.setString(10, doctor.getGender());
            ps.setString(11, doctor.getPhone());
            ps.setString(12, doctor.getId());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update doctor: " + doctor.getId(), e);
        }
    }

    @Override
    public void delete(Connection conn, String id) {
        String sql = "DELETE FROM doctor WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete doctor: " + id, e);
        }
    }

    @Override
    public Optional<Doctor> findById(Connection conn, String id) {
        String sql = "SELECT d.id, d.name, d.department_code, d.room, d.start_time, d.end_time, "
                   + "d.avg_consult_minutes, d.emergency_buffer_slots, d.status, d.age, d.gender, d.phone, "
                   + "dept.name AS department_name "
                   + "FROM doctor d "
                   + "LEFT JOIN department dept ON d.department_code = dept.code "
                   + "WHERE d.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDoctor(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find doctor by ID: " + id, e);
        }
    }

    @Override
    public List<Doctor> findAll(Connection conn) {
        String sql = "SELECT d.id, d.name, d.department_code, d.room, d.start_time, d.end_time, "
                   + "d.avg_consult_minutes, d.emergency_buffer_slots, d.status, d.age, d.gender, d.phone, "
                   + "dept.name AS department_name "
                   + "FROM doctor d "
                   + "LEFT JOIN department dept ON d.department_code = dept.code "
                   + "ORDER BY d.id ASC";
        List<Doctor> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapDoctor(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query all doctors", e);
        }
    }

    @Override
    public List<Doctor> findByDepartment(Connection conn, String deptCode) {
        String sql = "SELECT d.id, d.name, d.department_code, d.room, d.start_time, d.end_time, "
                   + "d.avg_consult_minutes, d.emergency_buffer_slots, d.status, d.age, d.gender, d.phone, "
                   + "dept.name AS department_name "
                   + "FROM doctor d "
                   + "LEFT JOIN department dept ON d.department_code = dept.code "
                   + "WHERE UPPER(d.department_code) = UPPER(?) "
                   + "ORDER BY d.id ASC";
        List<Doctor> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deptCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapDoctor(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query doctors by department: " + deptCode, e);
        }
    }

    @Override
    public int countWaiting(Connection conn, String doctorId) {
        String sql = "SELECT COUNT(*) FROM patient WHERE doctor_id = ? AND status = 'WAITING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count waiting patients for doctor: " + doctorId, e);
        }
    }

    private Doctor mapDoctor(ResultSet rs) throws SQLException {
        String deptName = rs.getString("department_name");
        String deptCode = rs.getString("department_code");
        String wh = rs.getString("start_time") + " - " + rs.getString("end_time");

        Doctor doc = new Doctor(
                rs.getString("id"),
                rs.getString("name"),
                rs.getInt("age"),
                rs.getString("gender"),
                rs.getString("phone"),
                deptName != null ? deptName : deptCode,
                deptCode,
                rs.getString("room"),
                wh,
                rs.getInt("avg_consult_minutes"),
                rs.getInt("emergency_buffer_slots")
        );
        doc.setStatus(DoctorStatus.fromString(rs.getString("status")));
        return doc;
    }
}
