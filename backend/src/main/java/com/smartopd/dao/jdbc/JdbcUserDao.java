package com.smartopd.dao.jdbc;

import com.smartopd.dao.DataAccessException;
import com.smartopd.dao.UserDao;
import com.smartopd.model.UserAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcUserDao - JDBC implementation of UserDao.
 * 
 * ============================================================================
 * OOP & Security: DAO Pattern & SQL Injection Defense
 * - ONLY PreparedStatement with bound parameters.
 * - Stores PBKDF2 hash digests and salt; plaintext passwords are never handled.
 * ============================================================================
 */
public class JdbcUserDao implements UserDao {

    @Override
    public void insert(Connection conn, UserAccount user) {
        String sql = "INSERT INTO user_account (username, password_hash, salt, role, display_name, doctor_id) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getSalt());
            ps.setString(4, user.getRole());
            ps.setString(5, user.getDisplayName());
            ps.setString(6, user.getDoctorId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert user account: " + user.getUsername(), e);
        }
    }

    @Override
    public Optional<UserAccount> findByUsername(Connection conn, String username) {
        String sql = "SELECT username, password_hash, salt, role, display_name, doctor_id "
                   + "FROM user_account WHERE UPPER(username) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username != null ? username.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new UserAccount(
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("role"),
                            rs.getString("display_name"),
                            rs.getString("doctor_id")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by username: " + username, e);
        }
    }

    @Override
    public List<UserAccount> findAll(Connection conn) {
        String sql = "SELECT username, password_hash, salt, role, display_name, doctor_id "
                   + "FROM user_account ORDER BY username ASC";
        List<UserAccount> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new UserAccount(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("salt"),
                        rs.getString("role"),
                        rs.getString("display_name"),
                        rs.getString("doctor_id")
                ));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query all user accounts", e);
        }
    }

    @Override
    public Optional<UserAccount> findByDoctorId(Connection conn, String doctorId) {
        String sql = "SELECT username, password_hash, salt, role, display_name, doctor_id "
                   + "FROM user_account WHERE doctor_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, doctorId != null ? doctorId.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new UserAccount(
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("role"),
                            rs.getString("display_name"),
                            rs.getString("doctor_id")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by doctorId: " + doctorId, e);
        }
    }

    @Override
    public int count(Connection conn) {
        String sql = "SELECT COUNT(*) FROM user_account";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count user accounts", e);
        }
    }

    @Override
    public void updatePassword(Connection conn, String username, String newPasswordHash, String newSalt) {
        String sql = "UPDATE user_account SET password_hash = ?, salt = ? WHERE UPPER(username) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setString(2, newSalt);
            ps.setString(3, username != null ? username.trim() : "");
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new DataAccessException("User not found for password update: " + username, null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update password for user: " + username, e);
        }
    }

    @Override
    public void deleteByUsername(Connection conn, String username) {
        String sql = "DELETE FROM user_account WHERE UPPER(username) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username != null ? username.trim() : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete user: " + username, e);
        }
    }
}
