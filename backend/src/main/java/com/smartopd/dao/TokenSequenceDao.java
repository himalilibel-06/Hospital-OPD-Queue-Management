package com.smartopd.dao;

import java.sql.Connection;

/**
 * TokenSequenceDao - Atomic generator and sequence tracker for department-category tokens.
 * 
 * ============================================================================
 * OOP Concept: Encapsulation & Concurrency Invariance
 * - Ensures atomic increment of sequential token numbers inside the caller's transaction.
 * ============================================================================
 */
public interface TokenSequenceDao {
    int nextValue(Connection conn, String deptCode, String categoryCode);
    void setLastValue(Connection conn, String deptCode, String categoryCode, int lastValue);
    int getLastValue(Connection conn, String deptCode, String categoryCode);
}
