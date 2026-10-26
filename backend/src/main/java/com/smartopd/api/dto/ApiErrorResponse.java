package com.smartopd.api.dto;

/**
 * ApiErrorResponse - Central error response structure.
 * 
 * ============================================================================
 * Format: {status, error, message, path, timestamp}
 * Guarantees zero internal SQL or stack trace leaks to API clients.
 * ============================================================================
 */
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {}
