package com.example.voting.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Every error leaves the API as {status, error, message} JSON. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Object> build(HttpStatusCode status, String message) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", resolved != null ? resolved.getReasonPhrase() : "Error");
        body.put("message", message);
        // Fixed JSON content type so errors still render when the client asked for text/event-stream.
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex) {
        log.warn("API Exception ({}): {}", ex.getStatus(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("Data integrity violation: {}", cause);
        if (cause != null && (cause.toLowerCase().contains("users_role_check") || cause.toLowerCase().contains("check constraint"))) {
            return build(HttpStatus.CONFLICT, "Database check constraint violation: " + cause);
        }
        return build(HttpStatus.CONFLICT, "That conflicts with existing data.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAny(Exception ex) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server.");
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull org.springframework.http.converter.HttpMessageNotReadableException ex,
                                                                   @NonNull HttpHeaders headers,
                                                                   @NonNull HttpStatusCode status,
                                                                   @NonNull WebRequest request) {
        log.warn("HTTP message not readable: {}", ex.getMessage());
        String cause = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return build(status, cause != null && !cause.isBlank() ? cause : "Failed to read request body.");
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(status, msg.isEmpty() ? "Invalid request." : msg);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex,
                                                             @Nullable Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode status,
                                                             @NonNull WebRequest request) {
        log.warn("Spring MVC error (status {}): {} ({})", status, ex.getMessage(), ex.getClass().getSimpleName());
        String msg = body instanceof ProblemDetail pd && pd.getDetail() != null
                ? pd.getDetail()
                : (ex.getMessage() != null && !ex.getMessage().isBlank() ? ex.getMessage() : "Request failed (" + status.value() + ").");
        return build(status, msg);
    }
}
