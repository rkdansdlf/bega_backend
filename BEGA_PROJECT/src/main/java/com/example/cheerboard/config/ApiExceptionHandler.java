package com.example.cheerboard.config;

import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_SELF_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_SELF_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_ERROR;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.RepostNotAllowedException;
import com.example.common.exception.RepostSelfNotAllowedException;
import com.example.common.exception.RepostTargetNotFoundException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cheerboard API 전용 예외 핸들러.
 */
@RestControllerAdvice(basePackages = "com.example.cheerboard")
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (isDeletedAuthorReference(ex)) {
            return build(HttpStatus.UNAUTHORIZED, "INVALID_AUTHOR", "인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        if (isRepostDuplicateViolation(ex)) {
            return build(HttpStatus.CONFLICT, REPOST_CONFLICT_CODE, REPOST_CONFLICT_ERROR);
        }

        return build(HttpStatus.BAD_REQUEST, "DATA_INTEGRITY_VIOLATION", "요청 데이터의 무결성 제약을 위반했습니다.");
    }

    @ExceptionHandler({RepostSelfNotAllowedException.class, RepostNotAllowedException.class})
    public ResponseEntity<ApiResponse> handleRepostNotAllowed(Exception ex) {
        String code = ex instanceof RepostNotAllowedException repostEx && repostEx.getErrorCode() != null
                ? repostEx.getErrorCode()
                : REPOST_NOT_ALLOWED_CODE;
        return build(HttpStatus.FORBIDDEN, code, resolveErrorMessage(code, ex.getMessage()));
    }

    @ExceptionHandler(RepostTargetNotFoundException.class)
    public ResponseEntity<ApiResponse> handleRepostTargetNotFound(RepostTargetNotFoundException ex) {
        String code = ex.getErrorCode() != null ? ex.getErrorCode() : REPOST_TARGET_NOT_FOUND_CODE;
        return build(HttpStatus.NOT_FOUND, code, resolveRepostMessage(code, ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiResponse> handleUnauthorized(AuthenticationCredentialsNotFoundException ex) {
        return build(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", defaultIfBlank(ex.getMessage(), "인증이 필요합니다."));
    }

    @ExceptionHandler(InvalidAuthorException.class)
    public ResponseEntity<ApiResponse> handleInvalidAuthor(InvalidAuthorException ex) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_AUTHOR", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse> handleForbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    private ResponseEntity<ApiResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status))
                .body(ApiResponse.error(code, defaultIfBlank(message, status.getReasonPhrase())));
    }

    private boolean isRepostDuplicateViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("uq_cheer_post_simple_repost")
                || (lower.contains("duplicate key") && lower.contains("repost_type") && lower.contains("repost_of_id"))
                || (lower.contains("repost_of_id") && lower.contains("repost_type"))
                || (lower.contains("cheer_post_repost") && lower.contains("duplicate key"))
                || (lower.contains("cheer_post_repost_pkey"));
    }

    private String resolveRepostMessage(String code, String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }

        if (REPOST_CYCLE_DETECTED_CODE.equals(code)) {
            return REPOST_CYCLE_DETECTED_ERROR;
        }
        if (REPOST_SELF_NOT_ALLOWED_CODE.equals(code)) {
            return REPOST_NOT_ALLOWED_SELF_ERROR;
        }
        if (REPOST_NOT_ALLOWED_CODE.equals(code)) {
            return REPOST_NOT_ALLOWED_ERROR;
        }
        if (REPOST_NOT_ALLOWED_BLOCKED_CODE.equals(code)) {
            return REPOST_NOT_ALLOWED_BLOCKED_ERROR;
        }
        if (REPOST_NOT_ALLOWED_PRIVATE_CODE.equals(code)) {
            return REPOST_NOT_ALLOWED_PRIVATE_ERROR;
        }
        if (REPOST_CANCEL_NOT_ALLOWED_CODE.equals(code)) {
            return REPOST_CANCEL_NOT_ALLOWED_ERROR;
        }
        if (REPOST_QUOTE_NOT_ALLOWED_CODE.equals(code)) {
            return REPOST_QUOTE_NOT_ALLOWED_ERROR;
        }
        if (REPOST_NOT_A_REPOST_CODE.equals(code)) {
            return REPOST_NOT_A_REPOST_ERROR;
        }
        if (REPOST_TARGET_NOT_FOUND_CODE.equals(code)) {
            return REPOST_TARGET_NOT_FOUND_ERROR;
        }

        return null;
    }

    private String resolveErrorMessage(String code, String message) {
        String resolved = resolveRepostMessage(code, message);
        return resolved != null ? resolved : message;
    }

    private boolean isDeletedAuthorReference(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause != null && cause.getMessage() != null
                ? cause.getMessage()
                : ex.getMessage();
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return lower.contains("foreign key")
                && lower.contains("author");
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
