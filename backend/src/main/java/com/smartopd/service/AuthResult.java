package com.smartopd.service;

/**
 * AuthResult - Value object returned upon successful user authentication.
 * 
 * ============================================================================
 * OOP Concept: Value Object / Data Transfer Object (DTO)
 * ============================================================================
 */
public record AuthResult(String username, String role, String displayName, String doctorId) {
}
