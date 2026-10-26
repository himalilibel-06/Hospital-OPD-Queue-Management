package com.smartopd.dao;

import com.smartopd.model.Doctor;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * DoctorDao - Repository interface for Doctor clinical providers.
 * 
 * ============================================================================
 * OOP Concept: Repository / DAO Pattern
 * ============================================================================
 */
public interface DoctorDao {
    void insert(Connection conn, Doctor doctor);
    void update(Connection conn, Doctor doctor);
    void delete(Connection conn, String id);
    Optional<Doctor> findById(Connection conn, String id);
    List<Doctor> findAll(Connection conn);
    List<Doctor> findByDepartment(Connection conn, String deptCode);
    int countWaiting(Connection conn, String doctorId);
}
