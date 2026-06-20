package com.scms.config;

import com.scms.service.AuthService.TooManyAttemptsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.*;

/**
 * GlobalExceptionHandler — ONE place that handles every exception in the app.
 *
 * MENTOR NOTE — @ControllerAdvice:
 * Every controller previously duplicated @ExceptionHandler methods.
 * @ControllerAdvice intercepts exceptions from ANY controller — one net, all caught.
 * Controllers are now 100% clean: no exception handling code inside them.
 *
 * Standard error envelope:
 * { "timestamp", "status", "error", "message", "path", "fields"? }
 * Consistent envelope = Axios interceptor reads err.response.data.message everywhere.
 *
 * MENTOR NOTE — HTTP 429:
 * Correct code for rate limiting / brute-force lockout.
 * Frontend uses it to show "wait N minutes" instead of generic error UI.
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

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            EntityNotFoundException ex, WebRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(envelope(404, "Not Found", ex.getMessage(), req, null));
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
        if (fields != null && !fields.isEmpty()) body.put("fields", fields);
        return body;
    }
}
