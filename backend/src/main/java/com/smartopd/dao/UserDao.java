package com.smartopd.dao;

import com.smartopd.model.UserAccount;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * UserDao - Repository interface for system user accounts (Front Desk & Doctors).
 * 
 * ============================================================================
 * OOP Concept: Repository / DAO Pattern
 * ============================================================================
 */
public interface UserDao {
    void insert(Connection conn, UserAccount user);
    Optional<UserAccount> findByUsername(Connection conn, String username);
    Optional<UserAccount> findByDoctorId(Connection conn, String doctorId);
    List<UserAccount> findAll(Connection conn);
    int count(Connection conn);
    void updatePassword(Connection conn, String username, String newPasswordHash, String newSalt);
    void deleteByUsername(Connection conn, String username);
}
