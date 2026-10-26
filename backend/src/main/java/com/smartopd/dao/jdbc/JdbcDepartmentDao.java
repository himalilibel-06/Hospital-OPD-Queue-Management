package com.smartopd.dao.jdbc;

import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.DepartmentDao;
import com.smartopd.model.Department;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcDepartmentDao - JDBC implementation of DepartmentDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & SQL Injection Defense
 * - Uses ONLY parameterized PreparedStatements with bound variables (never string concatenation).
 * - Guarantees safe escaping of all user input to thwart SQL injection attacks.
 * ============================================================================
 */
public class JdbcDepartmentDao implements DepartmentDao {

    @Override
    public void insert(Connection conn, Department department) {
        // SQL injection defense: PreparedStatement with bound parameters
        String sql = "INSERT INTO department (code, name) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, department.getCode());
            ps.setString(2, department.getName());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert department: " + department.getCode(), e);
        }
    }

    @Override
    public Optional<Department> findByCode(Connection conn, String code) {
        // SQL injection defense: Parameter binding
        String sql = "SELECT code, name FROM department WHERE UPPER(code) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Department(rs.getString("code"), rs.getString("name")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find department by code: " + code, e);
        }
    }

    @Override
    public List<Department> findAll(Connection conn) {
        String sql = "SELECT code, name FROM department ORDER BY code ASC";
        List<Department> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Department(rs.getString("code"), rs.getString("name")));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query departments", e);
        }
    }
}
