package com.smartopd.model;

import java.util.Objects;

/**
 * Token - Final, immutable value object representing a physical OPD waiting slip number.
 * Format: {DEPT_CODE}-{CATEGORY_CODE}-{SEQUENCE_PADDED_3} (e.g., CARD-E-014, PED-W-009).
 * 
 * ============================================================================
 * OOP Concept: Immutable Value Object & Encapsulation
 * - All fields are final and private.
 * - Value semantics guaranteed via equals(), hashCode(), and toString().
 * ============================================================================
 */
public final class Token implements Comparable<Token> {
    private final String deptCode;
    private final String categoryCode;
    private final int sequenceNumber;
    private final String value;

    public Token(String deptCode, String categoryCode, int sequenceNumber) {
        if (deptCode == null || deptCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Department code cannot be blank.");
        }
        if (categoryCode == null || categoryCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Category code cannot be blank.");
        }
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("Sequence number must be positive.");
        }

        this.deptCode = deptCode.trim().toUpperCase();
        this.categoryCode = categoryCode.trim().toUpperCase();
        this.sequenceNumber = sequenceNumber;
        this.value = String.format("%s-%s-%03d", this.deptCode, this.categoryCode, this.sequenceNumber);
    }

    public static Token parse(String tokenStr) {
        if (tokenStr == null) {
            throw new IllegalArgumentException("Token string cannot be null.");
        }
        String[] parts = tokenStr.trim().split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token format: " + tokenStr + " (Expected DEPT-CAT-NUM)");
        }
        try {
            int seq = Integer.parseInt(parts[2]);
            return new Token(parts[0], parts[1], seq);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid token sequence number: " + parts[2]);
        }
    }

    public String getDeptCode() {
        return deptCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return sequenceNumber == token.sequenceNumber &&
               Objects.equals(deptCode, token.deptCode) &&
               Objects.equals(categoryCode, token.categoryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deptCode, categoryCode, sequenceNumber);
    }

    @Override
    public int compareTo(Token other) {
        if (other == null) return 1;
        int deptCmp = this.deptCode.compareTo(other.deptCode);
        if (deptCmp != 0) return deptCmp;
        int catCmp = this.categoryCode.compareTo(other.categoryCode);
        if (catCmp != 0) return catCmp;
        return Integer.compare(this.sequenceNumber, other.sequenceNumber);
    }
}
