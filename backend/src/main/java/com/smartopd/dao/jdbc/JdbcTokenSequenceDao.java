package com.smartopd.dao.jdbc;

import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.TokenSequenceDao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JdbcTokenSequenceDao - JDBC implementation of TokenSequenceDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & Concurrency Safety
 * - Manages atomic sequence increments within the caller's active database transaction.
 * ============================================================================
 */
public class JdbcTokenSequenceDao implements TokenSequenceDao {

    @Override
    public int nextValue(Connection conn, String deptCode, String categoryCode) {
        String cleanDept = deptCode != null ? deptCode.trim().toUpperCase() : "GEN";
        String cleanCat = categoryCode != null ? categoryCode.trim().toUpperCase() : "W";

        String selectSql = "SELECT last_value FROM token_sequence WHERE department_code = ? AND category_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, cleanDept);
            ps.setString(2, cleanCat);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int next = rs.getInt("last_value") + 1;
                    String updateSql = "UPDATE token_sequence SET last_value = ? WHERE department_code = ? AND category_code = ?";
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setInt(1, next);
                        updatePs.setString(2, cleanDept);
                        updatePs.setString(3, cleanCat);
                        updatePs.executeUpdate();
                    }
                    return next;
                } else {
                    int next = 1;
                    String insertSql = "INSERT INTO token_sequence (department_code, category_code, last_value) VALUES (?, ?, ?)";
                    try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                        insertPs.setString(1, cleanDept);
                        insertPs.setString(2, cleanCat);
                        insertPs.setInt(3, next);
                        insertPs.executeUpdate();
                    }
                    return next;
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate next token sequence for " + cleanDept + "-" + cleanCat, e);
        }
    }

    @Override
    public void setLastValue(Connection conn, String deptCode, String categoryCode, int lastValue) {
        String cleanDept = deptCode != null ? deptCode.trim().toUpperCase() : "GEN";
        String cleanCat = categoryCode != null ? categoryCode.trim().toUpperCase() : "W";

        String sql = "INSERT INTO token_sequence (department_code, category_code, last_value) VALUES (?, ?, ?) "
                   + "ON CONFLICT(department_code, category_code) DO UPDATE SET last_value = excluded.last_value";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanDept);
            ps.setString(2, cleanCat);
            ps.setInt(3, lastValue);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set last value for " + cleanDept + "-" + cleanCat, e);
        }
    }

    @Override
    public int getLastValue(Connection conn, String deptCode, String categoryCode) {
        String cleanDept = deptCode != null ? deptCode.trim().toUpperCase() : "GEN";
        String cleanCat = categoryCode != null ? categoryCode.trim().toUpperCase() : "W";

        String sql = "SELECT last_value FROM token_sequence WHERE department_code = ? AND category_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanDept);
            ps.setString(2, cleanCat);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("last_value");
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get last value for " + cleanDept + "-" + cleanCat, e);
        }
    }
}
