package com.smartopd.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TokenGenerator - Thread-safe generator producing hospital OPD queue tokens.
 * 
 * ============================================================================
 * OOP Concept: Single Responsibility Principle (SRP) & Encapsulation
 * - Encapsulates token sequence state and string formatting rules.
 * - Adheres strictly to the frontend specification: {DEPT}-{CAT}-{001}.
 * ============================================================================
 */
public class TokenGenerator {
    private final AtomicInteger globalSequence = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> departmentSequences = new ConcurrentHashMap<>();

    public TokenGenerator() {
    }

    public TokenGenerator(int initialSequence) {
        this.globalSequence.set(initialSequence);
    }

    /**
     * Generates the next sequential token for a doctor and category.
     * Uses global patient count sequence matching frontend mock behavior.
     * 
     * @param doctor OPD doctor
     * @param category patient arrival category
     * @return newly generated Token
     */
    public Token nextToken(Doctor doctor, Category category) {
        String deptCode = doctor.getDeptCode();
        if (deptCode == null || deptCode.trim().isEmpty()) {
            String dept = doctor.getDepartment();
            deptCode = dept != null && dept.length() >= 4
                    ? dept.substring(0, 4).toUpperCase()
                    : (dept != null ? dept.toUpperCase() : "GEN");
        }
        return nextToken(deptCode, category);
    }

    /**
     * Generates token from explicit department code and category.
     * 
     * @param deptCode department code (e.g. CARD, PED, ORTH)
     * @param category patient arrival category
     * @return newly generated Token
     */
    public Token nextToken(String deptCode, Category category) {
        int seq = globalSequence.incrementAndGet();
        return new Token(deptCode, category.getCode(), seq);
    }

    /**
     * Generates token using department-isolated sequence counting.
     * 
     * @param deptCode department code
     * @param category patient arrival category
     * @return department-scoped Token
     */
    public Token nextDepartmentScopedToken(String deptCode, Category category) {
        String cleanCode = deptCode.trim().toUpperCase();
        AtomicInteger seqCounter = departmentSequences.computeIfAbsent(cleanCode, k -> new AtomicInteger(0));
        int seq = seqCounter.incrementAndGet();
        return new Token(cleanCode, category.getCode(), seq);
    }

    public int getCurrentGlobalSequence() {
        return globalSequence.get();
    }

    public void reset() {
        globalSequence.set(0);
        departmentSequences.clear();
    }
}
