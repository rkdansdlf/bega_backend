package com.example.cheerboard.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Cheerboard API 전용 예외 핸들러.
 *
 * <p>
 * 기존에는 서비스 계층에서 발생한 {@link AccessDeniedException} 이 컨트롤러까지
 * 전파되면서 500 응답이 내려가 프론트엔드에서 "서버 연결 실패"로 인식되었습니다.
 * 여기서는 인증/인가 오류를 401/403으로, 잘못된 입력이나 존재하지 않는 리소스는
 * 400/404로 매핑해 일관된 JSON 응답을 제공합니다.
 * </p>
 */
@RestControllerAdvice(basePackages = "com.example.cheerboard")
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(AuthenticationCredentialsNotFoundException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentNotValidException.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException manv
                ? manv.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(objectError -> objectError.getDefaultMessage())
                        .orElse("잘못된 요청입니다.")
                : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return build(ex.getStatusCode(), ex.getStatusCode().toString(), ex.getReason());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        return build((HttpStatusCode) status, code, message);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatusCode status, String code, String message) {
        String defaultMessage = message;
        if (defaultMessage == null || defaultMessage.isBlank()) {
            if (status instanceof HttpStatus httpStatus) {
                defaultMessage = httpStatus.getReasonPhrase();
            } else {
                defaultMessage = status.toString();
            }
        }

        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "code", code,
                "message", defaultMessage));
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * This helps debug 500 errors by returning the actual error message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Log the full stack trace for debugging
        ex.printStackTrace();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "서버 오류가 발생했습니다: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
    }
}