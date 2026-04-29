package com.example.common.exception;

import com.example.cheerboard.service.CheerServiceConstants;
import com.example.common.dto.ApiResponse;
import com.example.kbo.exception.TicketAnalysisException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 일괄 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String VALIDATION_ERROR_MESSAGE = "입력값을 확인해주세요.";
    private static final String GLOBAL_ERROR_KEY = "_global";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse> handleBusinessException(BusinessException e) {
        logByStatus(e.getStatus(), e);
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.error(e.getCode(), defaultIfBlank(e.getMessage(), "요청을 처리할 수 없습니다."), e.getData()));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiResponse> handleAuthenticationCredentialsNotFoundException(
            AuthenticationCredentialsNotFoundException e) {
        log.warn("AuthenticationCredentialsNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("AUTHENTICATION_REQUIRED", "인증이 필요합니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        return buildValidationErrorResponse(extractFieldErrors(e.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse> handleBindException(BindException e) {
        return buildValidationErrorResponse(extractFieldErrors(e.getBindingResult()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        return buildValidationErrorResponse(extractHandlerMethodValidationErrors(e));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleConstraintViolationException(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            putIfAbsent(errors, sanitizeConstraintPath(violation.getPropertyPath()), violation.getMessage());
        }
        return buildValidationErrorResponse(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = e.getName() + " 값 형식이 올바르지 않습니다.";
        return buildValidationErrorResponse(Map.of(e.getName(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        return buildValidationErrorResponse(Map.of(e.getParameterName(), "필수 파라미터입니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return buildValidationErrorResponse(Map.of(GLOBAL_ERROR_KEY, "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {
        log.warn("HttpMediaTypeNotSupportedException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(
                        "UNSUPPORTED_MEDIA_TYPE",
                        "요청 Content-Type이 올바르지 않습니다. multipart/form-data로 전송해주세요."));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("MaxUploadSizeExceededException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("MAX_UPLOAD_SIZE_EXCEEDED", "파일 크기가 제한을 초과했습니다. (최대 10MB)"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (isRepostDuplicateViolation(message)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(CheerServiceConstants.REPOST_CONFLICT_CODE, CheerServiceConstants.REPOST_CONFLICT_ERROR));
        }

        log.warn("DataIntegrityViolationException: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("DATA_INTEGRITY_VIOLATION", "요청 데이터의 무결성 제약을 위반했습니다."));
    }

    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<ApiResponse> handleTransientDataAccessException(
            TransientDataAccessException e) {
        return buildTemporaryDatabaseErrorResponse("Transient DB error", e);
    }

    @ExceptionHandler({CannotCreateTransactionException.class, CannotGetJdbcConnectionException.class})
    public ResponseEntity<ApiResponse> handleDatabaseConnectionException(Exception e) {
        return buildTemporaryDatabaseErrorResponse("Database connection error", e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", defaultIfBlank(ex.getMessage(), "잘못된 요청입니다.")));
    }

    // [Security] IllegalStateException은 도메인/라이브러리 내부 상태 오류에서 주로 던져지며,
    // 메시지에 Hibernate/Jackson/DB 드라이버 등의 내부 구현 정보가 섞여 나갈 위험이 있다.
    // prod에서 응답 바디에는 일반 메시지만 노출하고, 상세는 서버 로그에만 남긴다.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse> handleIllegalStateException(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", "요청을 처리할 수 없습니다."));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse> handleNoSuchElementException(NoSuchElementException ex) {
        log.warn("NoSuchElementException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", defaultIfBlank(ex.getMessage(), "요청한 데이터를 찾을 수 없습니다.")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("NoResourceFoundException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler({RepostNotAllowedException.class, RepostSelfNotAllowedException.class})
    public ResponseEntity<ApiResponse> handleRepostNotAllowed(Exception ex) {
        String code = ex instanceof RepostNotAllowedException repostEx && repostEx.getErrorCode() != null
                ? repostEx.getErrorCode()
                : CheerServiceConstants.REPOST_NOT_ALLOWED_CODE;
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(code, buildErrorMessageWithCode(code, ex.getMessage())));
    }

    @ExceptionHandler(RepostTargetNotFoundException.class)
    public ResponseEntity<ApiResponse> handleRepostTargetNotFoundException(RepostTargetNotFoundException ex) {
        String code = ex.getErrorCode() != null
                ? ex.getErrorCode()
                : CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(code, buildErrorMessageWithCode(code, ex.getMessage())));
    }

    @ExceptionHandler(TicketAnalysisException.class)
    public ResponseEntity<ApiResponse> handleTicketAnalysisException(TicketAnalysisException e) {
        log.warn("TicketAnalysisException status={} message={}", e.getStatus().value(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.error("TICKET_ANALYSIS_ERROR", defaultIfBlank(e.getMessage(), "티켓 분석에 실패했습니다.")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("AccessDeniedException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", defaultIfBlank(e.getMessage(), "접근 권한이 없습니다.")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        String message = ex.getReason();
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        String code = resolved != null ? resolved.name() : "RESPONSE_STATUS_EXCEPTION";
        String resolvedMessage = defaultIfBlank(message, resolved != null ? resolved.getReasonPhrase() : "요청을 처리할 수 없습니다.");

        logByStatus(statusCode, ex);
        return ResponseEntity
                .status(statusCode)
                .body(ApiResponse.error(code, resolvedMessage));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGlobalException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    private ResponseEntity<ApiResponse> buildValidationErrorResponse(Map<String, String> errors) {
        Map<String, String> resolvedErrors = errors.isEmpty()
                ? Map.of(GLOBAL_ERROR_KEY, VALIDATION_ERROR_MESSAGE)
                : errors;
        log.warn("Validation failed: {}", resolvedErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(VALIDATION_ERROR_CODE, VALIDATION_ERROR_MESSAGE, resolvedErrors));
    }

    private ResponseEntity<ApiResponse> buildTemporaryDatabaseErrorResponse(String logMessage, Exception e) {
        log.error("{}: {}", logMessage, e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("TEMPORARY_DATABASE_ERROR", "서버가 현재 혼잡합니다. 잠시 후 다시 시도해주세요."));
    }

    private Map<String, String> extractFieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            putIfAbsent(errors, fieldError.getField(), fieldError.getDefaultMessage());
        }
        for (ObjectError error : bindingResult.getGlobalErrors()) {
            putIfAbsent(errors, GLOBAL_ERROR_KEY, error.getDefaultMessage());
        }
        return errors;
    }

    private Map<String, String> extractHandlerMethodValidationErrors(HandlerMethodValidationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                for (FieldError fieldError : parameterErrors.getFieldErrors()) {
                    putIfAbsent(errors, fieldError.getField(), fieldError.getDefaultMessage());
                }
                for (ObjectError error : parameterErrors.getGlobalErrors()) {
                    putIfAbsent(errors, GLOBAL_ERROR_KEY, error.getDefaultMessage());
                }
                continue;
            }

            String key = resolveParameterKey(result);
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                putIfAbsent(errors, key, error.getDefaultMessage());
            }
        }
        return errors;
    }

    private String resolveParameterKey(ParameterValidationResult result) {
        String parameterName = result.getMethodParameter().getParameterName();
        String resolved = defaultIfBlank(parameterName, GLOBAL_ERROR_KEY);
        if (result.getContainerIndex() != null) {
            return resolved + "[" + result.getContainerIndex() + "]";
        }
        if (result.getContainerKey() != null) {
            return resolved + "[" + result.getContainerKey() + "]";
        }
        return resolved;
    }

    private String sanitizeConstraintPath(Path propertyPath) {
        if (propertyPath == null) {
            return GLOBAL_ERROR_KEY;
        }

        StringBuilder builder = new StringBuilder();
        boolean skippedMethodNode = false;
        for (Path.Node node : propertyPath) {
            String name = node.getName();
            if (name == null || name.isBlank() || "<cross-parameter>".equals(name)) {
                continue;
            }
            if (!skippedMethodNode) {
                skippedMethodNode = true;
                continue;
            }
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(name);
            if (node.getIndex() != null) {
                builder.append('[').append(node.getIndex()).append(']');
            }
            if (node.getKey() != null) {
                builder.append('[').append(node.getKey()).append(']');
            }
        }

        return builder.length() > 0 ? builder.toString() : GLOBAL_ERROR_KEY;
    }

    private void putIfAbsent(Map<String, String> errors, String key, String message) {
        errors.putIfAbsent(defaultIfBlank(key, GLOBAL_ERROR_KEY), defaultIfBlank(message, VALIDATION_ERROR_MESSAGE));
    }

    private void logByStatus(HttpStatusCode statusCode, Exception exception) {
        if (statusCode.is5xxServerError()) {
            log.error("{}: {}", exception.getClass().getSimpleName(), exception.getMessage(), exception);
        } else {
            log.warn("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String buildErrorMessageWithCode(String code, String message) {
        if (message == null || message.isBlank()) {
            if (CheerServiceConstants.REPOST_NOT_ALLOWED_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_NOT_ALLOWED_ERROR;
            }
            if (CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_ERROR;
            }
            if (CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_ERROR;
            }
            if (CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_TARGET_NOT_FOUND_ERROR;
            }
            if (CheerServiceConstants.REPOST_CONFLICT_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_CONFLICT_ERROR;
            }
            if (CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_ERROR;
            }
            if (CheerServiceConstants.REPOST_NOT_A_REPOST_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_NOT_A_REPOST_ERROR;
            }
            if (CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_ERROR;
            }
            if (CheerServiceConstants.REPOST_SELF_NOT_ALLOWED_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_NOT_ALLOWED_SELF_ERROR;
            }
            if (CheerServiceConstants.REPOST_CYCLE_DETECTED_CODE.equals(code)) {
                return CheerServiceConstants.REPOST_CYCLE_DETECTED_ERROR;
            }
            return "요청을 처리할 수 없습니다.";
        }
        return message;
    }

    private boolean isRepostDuplicateViolation(String message) {
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
}
