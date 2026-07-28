package com.muhammadminhaz.talkateeve.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Catch-all so no failure reaches the client without a stack trace in the logs.
 * Every handler logs the throwable itself, not just its message.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex, WebRequest request) {
        return log(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex, WebRequest request) {
        return log(HttpStatus.PAYLOAD_TOO_LARGE, ex, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex, WebRequest request) {
        return log(HttpStatus.valueOf(ex.getStatusCode().value()), ex, request);
    }

    /**
     * Without these two, the catch-all below turns every unknown URL and every
     * malformed JSON body into a 500 with a full stack trace, which buries the real
     * errors this handler exists to surface.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex, WebRequest request) {
        return log(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex, WebRequest request) {
        return log(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidBody(MethodArgumentNotValidException ex, WebRequest request) {
        return log(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex, WebRequest request) {
        return log(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
    }

    private ResponseEntity<Map<String, Object>> log(HttpStatus status, Exception ex, WebRequest request) {
        String errorId = UUID.randomUUID().toString();
        String path = request == null ? "unknown" : request.getDescription(false);

        if (status.is5xxServerError()) {
            log.error("[{}] {} on {} -> {}", errorId, ex.getClass().getSimpleName(), path, status, ex);
        } else {
            log.warn("[{}] {} on {} -> {}: {}", errorId, ex.getClass().getSimpleName(), path, status, ex.getMessage(), ex);
        }

        return ResponseEntity.status(status).body(Map.of(
                "errorId", errorId,
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", ex.getMessage() == null ? status.getReasonPhrase() : ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
