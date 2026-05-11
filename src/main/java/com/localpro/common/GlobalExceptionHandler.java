package com.localpro.common;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> fieldErrors.put(e.getField(), e.getDefaultMessage()));
        log.warn("=== VALIDATION ERROR [{}] {} => {}",
            request.getMethod(), request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(Map.of(
            "code", "VALIDATION_ERROR",
            "message", "Validation failed",
            "fields", fieldErrors,
            "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("=== REQUEST PARSE ERROR [{}] {} => {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
            "code", "INVALID_REQUEST_FORMAT",
            "message", "Could not parse request body",
            "detail", ex.getMostSpecificCause().getMessage(),
            "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("=== TYPE MISMATCH [{}] {} => param '{}' value '{}' expected {}",
            request.getMethod(), request.getRequestURI(),
            ex.getName(), ex.getValue(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return ResponseEntity.badRequest().body(Map.of(
            "code", "TYPE_MISMATCH",
            "message", "Invalid parameter: " + ex.getName(),
            "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("=== ACCESS DENIED [{}] {} => {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(403).body(Map.of(
            "code", "ACCESS_DENIED",
            "message", "You don't have permission to perform this action",
            "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("=== NOT FOUND [{}] {} => {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(404).body(Map.of(
            "code", "NOT_FOUND",
            "message", ex.getMessage(),
            "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("=== ILLEGAL ARGUMENT [{}] {} => {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
            "code", "BAD_REQUEST",
            "message", ex.getMessage(),
            "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("=== OPTIMISTIC LOCK CONFLICT [{}] {} => {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(409)
            .header("Retry-After", "1")
            .body(Map.of(
                "code", "CONFLICT",
                "message", "Resource was modified by another request, please retry",
                "path", request.getRequestURI()
            ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("=== DATABASE CONSTRAINT VIOLATION [{}] {} => {}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(409).body(Map.of(
            "code", "CONSTRAINT_VIOLATION",
            "message", "Database constraint violated",
            "detail", ex.getMessage(),
            "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("=== UNHANDLED ERROR [{}] {} => {} : {}",
            request.getMethod(), request.getRequestURI(),
            ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(500).body(Map.of(
            "code", "INTERNAL_ERROR",
            "message", ex.getMessage(),
            "type", ex.getClass().getSimpleName(),
            "path", request.getRequestURI()
        ));
    }
}
