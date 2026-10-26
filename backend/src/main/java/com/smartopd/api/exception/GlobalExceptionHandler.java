package com.smartopd.api.exception;

import com.smartopd.api.dto.ApiErrorResponse;
import com.smartopd.api.security.AccountLockedException;
import com.smartopd.api.security.RateLimitExceededException;
import com.smartopd.dao.DataAccessException;
import com.smartopd.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler - Central exception translator returning standardized JSON.
 * 
 * ============================================================================
 * OOP & Architectural Concepts:
 * - Centralized Exception Handling: Transforms business domain exceptions into
 *   RFC-compliant HTTP status codes.
 * - Defensive Error Masking: Never leaks SQL statements, database schemas, or
 *   raw stack traces in 500 errors.
 * ============================================================================
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        if (details.isBlank()) details = "Request payload failed validation.";

        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                details,
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({ValidationException.class, InvalidSeverityException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequestExceptions(Exception ex, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage() != null ? ex.getMessage() : "Invalid request parameters.",
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({DoctorUnavailableException.class, DuplicateTokenException.class, SlotFullException.class, ConflictException.class})
    public ResponseEntity<ApiErrorResponse> handleConflictExceptions(Exception ex, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage() != null ? ex.getMessage() : "Resource state conflict.",
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFoundExceptions(Exception ex, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage() != null ? ex.getMessage() : "Requested resource was not found.",
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler({AccountLockedException.class, RateLimitExceededException.class})
    public ResponseEntity<ApiErrorResponse> handleRateLimitAndAccountLocked(RuntimeException ex, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        // Safe generic message: NEVER leak raw SQL syntax, table names, or JDBC traces
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An internal database error occurred. Please contact the system administrator.",
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected internal server error occurred. Please try again.",
                request.getRequestURI(),
                Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
