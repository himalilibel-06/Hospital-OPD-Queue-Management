package com.smartopd.dao;

import com.smartopd.model.Patient;
import com.smartopd.model.PatientStatus;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * PatientDao - Repository interface for OPD Patients.
 * 
 * ============================================================================
 * OOP Concept: Repository / DAO Pattern
 * ============================================================================
 */
public interface PatientDao {
    void insert(Connection conn, Patient patient);
    void update(Connection conn, Patient patient);
    Optional<Patient> findByToken(Connection conn, String token);
    Optional<Patient> findById(Connection conn, String id);
    List<Patient> findWaitingByDoctor(Connection conn, String doctorId);
    List<Patient> findAllToday(Connection conn);
    List<Patient> findByStatus(Connection conn, PatientStatus status);
    List<Patient> findAll(Connection conn);
}
