package com.smartopd.dao.jdbc;

import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.PatientDao;
import com.smartopd.model.Category;
import com.smartopd.model.Patient;
import com.smartopd.model.PatientFactory;
import com.smartopd.model.PatientStatus;
import com.smartopd.model.Token;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcPatientDao - JDBC implementation of PatientDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & SQL Injection Defense
 * - ONLY PreparedStatement with bound parameters is used to defend against SQL injection.
 * - Subclass reconstruction is delegated to PatientFactory to restore correct OOP polymorphism.
 * ============================================================================
 */
public class JdbcPatientDao implements PatientDao {

    @Override
    public void insert(Connection conn, Patient patient) {
        // SQL injection defense: Parameter binding
        String sql = "INSERT INTO patient (id, token, name, age, gender, phone, category, severity, "
                   + "doctor_id, registered_at, appointment_time, chief_complaint, status, skipped, "
                   + "consult_start, consult_end) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patient.getId());
            ps.setString(2, patient.getToken().getValue());
            ps.setString(3, patient.getName());
            ps.setInt(4, patient.getAge());
            ps.setString(5, patient.getGender());
            ps.setString(6, patient.getPhone());
            ps.setString(7, patient.getCategory().name());
            ps.setInt(8, patient.getSeverity());
            ps.setString(9, patient.getDoctorId());
            ps.setString(10, patient.getRegisteredAt().toString());
            ps.setString(11, patient.getAppointmentTime());
            ps.setString(12, patient.getChiefComplaint());
            ps.setString(13, patient.getStatus().name());
            ps.setInt(14, patient.isSkipped() ? 1 : 0);
            ps.setString(15, patient.getConsultationStartTime() != null ? patient.getConsultationStartTime().toString() : null);
            ps.setString(16, patient.getConsultationEndTime() != null ? patient.getConsultationEndTime().toString() : null);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert patient: " + patient.getId(), e);
        }
    }

    @Override
    public void update(Connection conn, Patient patient) {
        // SQL injection defense: Parameter binding
        String sql = "UPDATE patient SET token = ?, name = ?, age = ?, gender = ?, phone = ?, "
                   + "category = ?, severity = ?, doctor_id = ?, registered_at = ?, appointment_time = ?, "
                   + "chief_complaint = ?, status = ?, skipped = ?, consult_start = ?, consult_end = ? "
                   + "WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patient.getToken().getValue());
            ps.setString(2, patient.getName());
            ps.setInt(3, patient.getAge());
            ps.setString(4, patient.getGender());
            ps.setString(5, patient.getPhone());
            ps.setString(6, patient.getCategory().name());
            ps.setInt(7, patient.getSeverity());
            ps.setString(8, patient.getDoctorId());
            ps.setString(9, patient.getRegisteredAt().toString());
            ps.setString(10, patient.getAppointmentTime());
            ps.setString(11, patient.getChiefComplaint());
            ps.setString(12, patient.getStatus().name());
            ps.setInt(13, patient.isSkipped() ? 1 : 0);
            ps.setString(14, patient.getConsultationStartTime() != null ? patient.getConsultationStartTime().toString() : null);
            ps.setString(15, patient.getConsultationEndTime() != null ? patient.getConsultationEndTime().toString() : null);
            ps.setString(16, patient.getId());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update patient: " + patient.getId(), e);
        }
    }

    @Override
    public Optional<Patient> findByToken(Connection conn, String token) {
        // Case-insensitive token lookup with bound parameter
        String sql = baseSelectSql() + " WHERE UPPER(p.token) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token != null ? token.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPatient(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find patient by token: " + token, e);
        }
    }

    @Override
    public Optional<Patient> findById(Connection conn, String id) {
        String sql = baseSelectSql() + " WHERE p.id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPatient(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find patient by ID: " + id, e);
        }
    }

    @Override
    public List<Patient> findWaitingByDoctor(Connection conn, String doctorId) {
        String sql = baseSelectSql() + " WHERE p.doctor_id = ? AND p.status = 'WAITING' ORDER BY p.registered_at ASC";
        List<Patient> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPatient(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find waiting patients for doctor: " + doctorId, e);
        }
    }

    @Override
    public List<Patient> findAllToday(Connection conn) {
        String sql = baseSelectSql() + " ORDER BY p.registered_at ASC";
        List<Patient> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapPatient(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query all patients today", e);
        }
    }

    @Override
    public List<Patient> findByStatus(Connection conn, PatientStatus status) {
        String sql = baseSelectSql() + " WHERE p.status = ? ORDER BY p.registered_at ASC";
        List<Patient> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPatient(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find patients by status: " + status, e);
        }
    }

    @Override
    public List<Patient> findAll(Connection conn) {
        return findAllToday(conn);
    }

    private String baseSelectSql() {
        return "SELECT p.id, p.token, p.name, p.age, p.gender, p.phone, p.category, p.severity, "
             + "p.doctor_id, p.registered_at, p.appointment_time, p.chief_complaint, "
             + "p.status, p.skipped, p.consult_start, p.consult_end, "
             + "d.name AS doctor_name, d.room AS doctor_room, d.department_code, "
             + "dept.name AS department_name "
             + "FROM patient p "
             + "LEFT JOIN doctor d ON p.doctor_id = d.id "
             + "LEFT JOIN department dept ON d.department_code = dept.code";
    }

    private Patient mapPatient(ResultSet rs) throws SQLException {
        String tokenStr = rs.getString("token");
        Token token = Token.parse(tokenStr);

        String regStr = rs.getString("registered_at");
        Instant registeredAt = regStr != null ? Instant.parse(regStr) : Instant.now();

        String cStartStr = rs.getString("consult_start");
        Instant consultStart = cStartStr != null ? Instant.parse(cStartStr) : null;

        String cEndStr = rs.getString("consult_end");
        Instant consultEnd = cEndStr != null ? Instant.parse(cEndStr) : null;

        String deptName = rs.getString("department_name");
        String deptCode = rs.getString("department_code");

        return PatientFactory.reconstructPatient(
                rs.getString("id"),
                rs.getString("name"),
                rs.getInt("age"),
                rs.getString("gender"),
                rs.getString("phone"),
                token,
                rs.getString("doctor_id"),
                rs.getString("doctor_name"),
                rs.getString("doctor_room"),
                deptName != null ? deptName : deptCode,
                rs.getInt("severity"),
                Category.fromString(rs.getString("category")),
                registeredAt,
                rs.getString("appointment_time"),
                rs.getString("chief_complaint"),
                PatientStatus.fromString(rs.getString("status")),
                rs.getInt("skipped") == 1,
                consultStart,
                consultEnd
        );
    }
}
