package com.example.docplatform.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Must be handled explicitly: the catch-all Exception handler below takes
    // precedence over @ResponseStatus on the exception class, which silently
    // turned quota rejections into 500s under load.
    @ExceptionHandler(TenantQuotaExceededException.class)
    public ResponseEntity<Map<String, String>> quotaExceeded(TenantQuotaExceededException ex) {
        return ResponseEntity.status(429).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, String>> unauthorized(RuntimeException ex) {
        return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException ex) {
        return ResponseEntity.status(400).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generic(Exception ex) {
        log.error("Unhandled exception reached the API boundary", ex);
        return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
    }
}
