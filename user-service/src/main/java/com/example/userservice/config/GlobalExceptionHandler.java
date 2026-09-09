package com.example.userservice.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> formatFieldError(error))
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", errorMessage));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    private String formatFieldError(FieldError error) {
        String field = error.getField();
        if ("email".equalsIgnoreCase(field)) {
            return "Please provide a valid email address (e.g. user@example.com)";
        }
        if ("password".equalsIgnoreCase(field)) {
            return "Password must be at least 6 characters";
        }
        if ("username".equalsIgnoreCase(field)) {
            return "Username must be between 3 and 50 characters";
        }
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : field + " is invalid";
    }
}
