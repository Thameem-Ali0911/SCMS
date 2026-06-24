package com.scms.config;

import com.scms.service.AuthService.TooManyAttemptsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/**
 * GlobalExceptionHandler — ONE place that handles every exception in the app.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • Added OptimisticLockingFailureException → 409 Conflict. v1.3 didn't
 *     handle this at all — a concurrent edit conflict (two staff updating
 *     the same complaint at once, which @Version on Complaint is specifically
 *     designed to detect) fell through to the generic 500 handler, which is
 *     misleading: 500 says "we broke", 409 correctly says "you and someone
 *     else collided, please retry."
 *
 *   • Added IllegalStateException → 409 Conflict, used by
 *     ComplaintStatusPolicy to reject illegal status transitions
 *     (REJECTED → SUBMITTED, etc.) — see ComplaintService.updateStatus().
 *
 *   • Added ResponseStatusException passthrough — services that throw this
 *     directly (e.g. CategoryService) get their chosen status code and
 *     reason respected, in the same envelope shape as everything else.
 *
 *   • Every envelope now includes `requestId` from the MDC, populated by
 *     RequestIdFilter — this is what makes "trace a single request across
 *     multiple log lines" (a v1.3 Logging finding) actually possible: a
 *     user/operator can correlate an error response directly to the exact
 *     log lines produced while handling it.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(
            TooManyAttemptsException ex, WebRequest req) {
        log.warn("Rate limit: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(envelope(429, "Too Many Requests", ex.getMessage(), req, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(envelope(400, "Validation Failed",
                        "One or more fields are invalid.", req, fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(
            IllegalArgumentException ex, WebRequest req) {
        return ResponseEntity.badRequest()
                .body(envelope(400, "Bad Request", ex.getMessage(), req, null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex, WebRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(envelope(409, "Conflict", ex.getMessage(), req, null));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            OptimisticLockingFailureException ex, WebRequest req) {
        log.warn("Optimistic lock conflict [{}]: {}", req.getDescription(false), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(envelope(409, "Conflict",
                        "This record was changed by someone else. Please refresh and try again.", req, null));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            EntityNotFoundException ex, WebRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(envelope(404, "Not Found", ex.getMessage(), req, null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex, WebRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(envelope(status.value(), status.getReasonPhrase(),
                        ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(), req, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest req) {
        log.warn("Access denied [{}]: {}", req.getDescription(false), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(envelope(403, "Forbidden", ex.getMessage(), req, null));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex, WebRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(envelope(401, "Unauthorized", ex.getMessage(), req, null));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(WebRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(envelope(403, "Account Disabled",
                        "Your account has been deactivated. Contact your administrator.",
                        req, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex, WebRequest req) {
        log.error("Unhandled [{}]: {}", req.getDescription(false), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(envelope(500, "Internal Server Error",
                        "Something went wrong. Please try again later.", req, null));
    }

    private Map<String, Object> envelope(int status, String error, String message,
                                          WebRequest req, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status);
        body.put("error",     error);
        body.put("message",   message);
        body.put("path",      req.getDescription(false).replace("uri=", ""));
        String requestId = MDC.get("requestId");
        if (requestId != null) body.put("requestId", requestId);
        if (fields != null && !fields.isEmpty()) body.put("fields", fields);
        return body;
    }
}
