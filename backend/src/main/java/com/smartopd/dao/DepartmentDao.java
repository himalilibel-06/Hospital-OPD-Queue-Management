package com.smartopd.dao;

import com.smartopd.model.Department;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * DepartmentDao - Repository interface for OPD clinical departments.
 * 
 * ============================================================================
 * OOP Concept: Repository / DAO Pattern (Data Access Object)
 * - Decouples domain entities and business logic from SQL and database storage.
 * ============================================================================
 */
public interface DepartmentDao {
    void insert(Connection conn, Department department);
    Optional<Department> findByCode(Connection conn, String code);
    List<Department> findAll(Connection conn);
}
