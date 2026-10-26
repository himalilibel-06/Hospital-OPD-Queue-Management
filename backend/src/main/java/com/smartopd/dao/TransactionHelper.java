package com.smartopd.dao;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * TransactionHelper - Coordinates transactional Units of Work for ACID guarantees.
 * 
 * ============================================================================
 * OOP Concept: Command / Callback Pattern & Resource Management (Unit of Work)
 * - Encapsulates transaction demarcations: begin, commit on success, rollback on error.
 * - Guarantees proper connection cleanup using try-with-resources.
 * ============================================================================
 */
public class TransactionHelper {
    private final ConnectionProvider connectionProvider;

    public TransactionHelper(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider cannot be null.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Functional interface representing a block of DAO operations within a transaction.
     * 
     * @param <T> result type returned by the transaction
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T doInTransaction(Connection conn) throws Exception;
    }

    /**
     * Executes a unit of work inside a single ACID database transaction.
     * 
     * @param callback lambda or functional block containing DAO operations
     * @param <T> return type
     * @return operation result
     */
    public <T> T execute(TransactionCallback<T> callback) {
        try (Connection conn = connectionProvider.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = callback.doInTransaction(conn);
                conn.commit();
                return result;
            } catch (Exception ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new DataAccessException("Transaction rolled back due to failure: " + ex.getMessage(), ex);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException connEx) {
            throw new DataAccessException("Failed to acquire connection for transaction", connEx);
        }
    }
}
