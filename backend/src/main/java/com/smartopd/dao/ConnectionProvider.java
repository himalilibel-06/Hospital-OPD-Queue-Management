package com.smartopd.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * ConnectionProvider - Manages SQLite database connectivity and PRAGMA settings.
 * 
 * ============================================================================
 * OOP Concept: Single Responsibility Principle (SRP) & Factory / Provider Pattern
 * 
 * SQLite Single-Writer Architecture & Concurrency Notes (For Viva Defense):
 * ----------------------------------------------------------------------------
 * SQLite utilizes a file-based lock model. With Write-Ahead Logging (WAL) enabled,
 * SQLite allows concurrent readers alongside one active writer. However, only ONE
 * writer thread can mutate the database at any moment in time.
 * 
 * If a second thread attempts to write concurrently, SQLite waits up to the configured
 * busy timeout (PRAGMA busy_timeout = 5000 ms). To completely avoid SQLITE_BUSY
 * lock contention and guarantee predictable ACID guarantees, all mutating write
 * operations in SmartOPD are:
 * 1. Executed as short, highly focused transactions.
 * 2. Serialized through a reentrant mutex lock in QueueService.
 * ============================================================================
 */
public class ConnectionProvider {
    public static final String DB_PATH_PROPERTY = "smartopd.db.path";
    public static final String DB_PATH_ENV = "SMARTOPD_DB_PATH";
    public static final String DEFAULT_DB_PATH = "data/smartopd.db";

    private final String dbPath;

    public ConnectionProvider() {
        this(resolveDbPath());
    }

    public ConnectionProvider(String dbPath) {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            this.dbPath = resolveDbPath();
        } else {
            this.dbPath = dbPath.trim();
        }
        ensureParentDirectoryExists(this.dbPath);
    }

    public static String resolveDbPath() {
        String prop = System.getProperty(DB_PATH_PROPERTY);
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        String env = System.getenv(DB_PATH_ENV);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return DEFAULT_DB_PATH;
    }

    private void ensureParentDirectoryExists(String path) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        } catch (Exception e) {
            throw new DataAccessException("Could not create database directory for path: " + path, e);
        }
    }

    /**
     * Obtains a new JDBC connection with foreign keys, WAL mode, and busy timeout configured.
     * 
     * @return active configured Connection
     * @throws DataAccessException if connection fails
     */
    public Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = conn.createStatement()) {
                // Enforce relational foreign key constraints
                stmt.execute("PRAGMA foreign_keys = ON;");
                // Enable Write-Ahead Logging (WAL) for concurrent read performance
                stmt.execute("PRAGMA journal_mode = WAL;");
                // Wait up to 5,000 milliseconds when acquiring locks before throwing SQLITE_BUSY
                stmt.execute("PRAGMA busy_timeout = 5000;");
            }
            return conn;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to connect to SQLite database at: " + dbPath, e);
        }
    }

    public String getDbPath() {
        return dbPath;
    }
}
