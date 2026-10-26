package com.smartopd.dao;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * SchemaInitializer - Idempotently creates database tables, constraints, and indexes.
 * 
 * ============================================================================
 * OOP Concept: Single Responsibility Principle & Resource Encapsulation
 * - Loads src/main/resources/schema.sql from the classpath and executes statements.
 * ============================================================================
 */
public class SchemaInitializer {
    private final ConnectionProvider connectionProvider;

    public SchemaInitializer(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider cannot be null.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Executes schema.sql statements idempotently against the configured database.
     */
    public void initialize() {
        try (Connection conn = connectionProvider.getConnection();
             InputStream in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                throw new DataAccessException("Resource schema.sql not found in application classpath.");
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("--") && !trimmed.isEmpty()) {
                        sb.append(line).append("\n");
                    }
                }
            }

            String[] sqlStatements = sb.toString().split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String rawSql : sqlStatements) {
                    String sql = rawSql.trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof DataAccessException) {
                throw (DataAccessException) e;
            }
            throw new DataAccessException("Failed to initialize database schema: " + e.getMessage(), e);
        }
    }
}
