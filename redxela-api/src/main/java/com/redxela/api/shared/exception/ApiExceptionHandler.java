package com.redxela.api.shared.exception;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Los errores HTTP no incluyen trazas ni mensajes internos de excepciones inesperadas
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex) { return error(404, ex.getMessage(), List.of()); }
    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex) { return error(409, ex.getMessage(), List.of()); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException ex) { return error(400, ex.getMessage(), List.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        return error(400, "Datos inválidos", ex.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage()).sorted().toList());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception ex) { return error(400, "JSON o identificador inválido", List.of()); }
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiError> unexpected(RuntimeException ex) {
        log.error("Fallo inesperado procesando la solicitud", ex);
        return error(500, "No se pudo completar la operación", List.of());
    }
    private ResponseEntity<ApiError> error(int status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(status, message, details, Instant.now()));
    }
    public record ApiError(int status, String message, List<String> details, Instant timestamp) {}
}
