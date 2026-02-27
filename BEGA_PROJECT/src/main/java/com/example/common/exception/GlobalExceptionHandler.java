package com.example.common.exception;

import com.example.BegaDiary.Exception.DiaryAlreadyExistsException;
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Exception.GameNotFoundException;
import com.example.BegaDiary.Exception.ImageProcessingException;
import com.example.BegaDiary.Exception.WinningNameNotFoundException;
import com.example.admin.exception.InsufficientPrivilegeException;
import com.example.admin.exception.InvalidRoleChangeException;
import com.example.common.dto.ApiResponse;
import com.example.mate.exception.DuplicateApplicationException;
import com.example.mate.exception.DuplicateCheckInException;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.InvalidPartyStatusException;
import com.example.mate.exception.PartyApplicationNotFoundException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.notification.exception.NotificationNotFoundException;
import com.example.stadium.exception.StadiumNotFoundException;
import com.example.cheerboard.service.CheerServiceConstants;
import com.example.kbo.exception.TicketAnalysisException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 일괄 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 Not Found - 사용자를 찾을 수 없음
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse> handleUserNotFoundException(UserNotFoundException e) {
        log.info("UserNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 404 Not Found - 팀을 찾을 수 없음
     */
    @ExceptionHandler(TeamNotFoundException.class)
    public ResponseEntity<ApiResponse> handleTeamNotFoundException(TeamNotFoundException e) {
        log.warn("TeamNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 409 Conflict - 중복된 이메일
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse> handleDuplicateEmailException(DuplicateEmailException e) {
        log.warn("DuplicateEmailException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 401 Unauthorized - 인증 실패
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse> handleInvalidCredentialsException(InvalidCredentialsException e) {
        log.warn("InvalidCredentialsException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(InvalidAuthorException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAuthorException(InvalidAuthorException e) {
        log.warn("InvalidAuthorException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "success", false,
                        "code", "INVALID_AUTHOR",
                        "message", e.getMessage()));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationCredentialsNotFoundException(
            AuthenticationCredentialsNotFoundException e) {
        log.warn("AuthenticationCredentialsNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "success", false,
                        "code", "INVALID_AUTHOR",
                        "message", "인증이 필요합니다."));
    }

    /**
     * 403 Forbidden - 소셜 로그인 필요
     */
    @ExceptionHandler(SocialLoginRequiredException.class)
    public ResponseEntity<ApiResponse> handleSocialLoginRequiredException(SocialLoginRequiredException e) {
        log.warn("SocialLoginRequiredException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 403 Forbidden - 본인인증(소셜 연동) 필요
     */
    @ExceptionHandler(IdentityVerificationRequiredException.class)
    public ResponseEntity<ApiResponse> handleIdentityVerificationRequiredException(
            IdentityVerificationRequiredException e) {
        log.warn("IdentityVerificationRequiredException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 400 Bad Request - 잘못된 입력값 (Validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName;
            if (error instanceof FieldError fieldError) {
                fieldName = fieldError.getField();
            } else {
                fieldName = error.getObjectName();
            }
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("입력값이 올바르지 않습니다.", errors));
    }

    /**
     * 400 Bad Request - 일반적인 잘못된 요청
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(PolicyConsentException.class)
    public ResponseEntity<ApiResponse> handlePolicyConsentException(PolicyConsentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        e.getMessage(),
                        Map.of(
                                "code", e.getCode(),
                                "policyTypes", e.getPolicyTypes())));
    }

    @ExceptionHandler(TicketAnalysisException.class)
    public ResponseEntity<ApiResponse> handleTicketAnalysisException(TicketAnalysisException e) {
        log.warn("TicketAnalysisException status={} message={}", e.getStatus().value(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse> handleRateLimitExceededException(RateLimitExceededException e) {
        log.warn("RateLimitExceededException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {
        log.warn("HttpMediaTypeNotSupportedException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("요청 Content-Type이 올바르지 않습니다. multipart/form-data로 전송해주세요."));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("MaxUploadSizeExceededException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("파일 크기가 제한을 초과했습니다. (최대 10MB)"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (isRepostDuplicateViolation(message)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(buildErrorBodyWithCode(CheerServiceConstants.REPOST_CONFLICT_CODE, null));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorBodyWithCode("DATA_INTEGRITY_VIOLATION", "요청 데이터의 무결성 제약을 위반했습니다."));
    }

    @ExceptionHandler({ IllegalStateException.class })
    public ResponseEntity<ApiResponse> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse> handleNoSuchElementException(NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * 404 Not Found - 정적 리소스/매핑 없는 경로
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("NoResourceFoundException: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler({ RepostNotAllowedException.class, RepostSelfNotAllowedException.class })
    public ResponseEntity<Map<String, Object>> handleRepostNotAllowed(Exception ex) {
        String code = ex instanceof RepostNotAllowedException repostEx && repostEx.getErrorCode() != null
                ? repostEx.getErrorCode()
                : CheerServiceConstants.REPOST_NOT_ALLOWED_CODE;
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildErrorBodyWithCode(code, ex.getMessage()));
    }

    @ExceptionHandler(RepostTargetNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRepostTargetNotFoundException(RepostTargetNotFoundException ex) {
        String code = ex.getErrorCode() != null
                ? ex.getErrorCode()
                : CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildErrorBodyWithCode(code, ex.getMessage()));
    }

    /**
     * 500 Internal Server Error - 예상하지 못한 모든 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGlobalException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
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

    private Map<String, Object> buildErrorBodyWithCode(String code, String message) {
        return Map.of(
                "success", false,
                "code", code,
                "message", buildErrorMessageWithCode(code, message));
    }

    // ------ stadiumguide 관련 예외 ----------
    /**
     * 404 Not Found - 경기장을 찾을 수 없음
     */
    @ExceptionHandler(StadiumNotFoundException.class)
    public ResponseEntity<ApiResponse> handleStadiumNotFoundException(StadiumNotFoundException e) {
        log.warn("StadiumNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    // -------- Mate 관련 예외 ------------
    /**
     * 404 Not Found - 파티를 찾을 수 없음
     */
    @ExceptionHandler(PartyNotFoundException.class)
    public ResponseEntity<ApiResponse> handlePartyNotFoundException(PartyNotFoundException e) {
        log.warn("PartyNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 404 Not Found - 신청을 찾을 수 없음
     */
    @ExceptionHandler(PartyApplicationNotFoundException.class)
    public ResponseEntity<ApiResponse> handlePartyApplicationNotFoundException(PartyApplicationNotFoundException e) {
        log.warn("PartyApplicationNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 409 Conflict - 중복 신청
     */
    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<ApiResponse> handleDuplicateApplicationException(DuplicateApplicationException e) {
        log.warn("DuplicateApplicationException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 409 Conflict - 중복 체크인
     */
    @ExceptionHandler(DuplicateCheckInException.class)
    public ResponseEntity<ApiResponse> handleDuplicateCheckInException(DuplicateCheckInException e) {
        log.warn("DuplicateCheckInException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 400 Bad Request - 파티가 가득 참
     */
    @ExceptionHandler(PartyFullException.class)
    public ResponseEntity<ApiResponse> handlePartyFullException(PartyFullException e) {
        log.warn("PartyFullException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 400 Bad Request - 잘못된 신청 상태
     */
    @ExceptionHandler(InvalidApplicationStatusException.class)
    public ResponseEntity<ApiResponse> handleInvalidApplicationStatusException(InvalidApplicationStatusException e) {
        log.warn("InvalidApplicationStatusException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 400 Bad Request - 잘못된 파티 상태
     */
    @ExceptionHandler(InvalidPartyStatusException.class)
    public ResponseEntity<ApiResponse> handleInvalidPartyStatusException(InvalidPartyStatusException e) {
        log.warn("InvalidPartyStatusException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 403 Forbidden - 권한 없음
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse> handleUnauthorizedAccessException(UnauthorizedAccessException e) {
        log.warn("UnauthorizedAccessException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse> handleNotificationNotFoundException(NotificationNotFoundException e) {
        log.warn("NotificationNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    // --- BegaDiary 관련 예외 추가 ---

    /**
     * 404 Not Found - 경기 정보를 찾을 수 없음
     */
    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<ApiResponse> handleGameNotFoundException(GameNotFoundException e) {
        log.warn("GameNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 404 Not Found - 다이어리를 찾을 수 없음
     */
    @ExceptionHandler(DiaryNotFoundException.class)
    public ResponseEntity<ApiResponse> handleDiaryNotFoundException(DiaryNotFoundException e) {
        log.warn("DiaryNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 409 Conflict - 이미 작성된 다이어리 존재
     */
    @ExceptionHandler(DiaryAlreadyExistsException.class)
    public ResponseEntity<ApiResponse> handleDiaryAlreadyExistsException(DiaryAlreadyExistsException e) {
        log.warn("DiaryAlreadyExistsException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 500 Internal Server Error - 이미지 처리 중 오류 발생
     */
    @ExceptionHandler(ImageProcessingException.class)
    public ResponseEntity<ApiResponse> handleImageProcessingException(ImageProcessingException e) {
        log.error("ImageProcessingException: {}", e.getMessage()); // 시스템 오류이므로 error 레벨 로그 권장
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(WinningNameNotFoundException.class)
    public ResponseEntity<ApiResponse> handleWinningNameNotFoundException(WinningNameNotFoundException e) {
        log.error("WinningNameNotFoundException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    // -------- Admin Role 관련 예외 ------------

    /**
     * 403 Forbidden - 권한 부족 (SUPER_ADMIN 필요)
     */
    @ExceptionHandler(InsufficientPrivilegeException.class)
    public ResponseEntity<ApiResponse> handleInsufficientPrivilegeException(InsufficientPrivilegeException e) {
        log.warn("InsufficientPrivilegeException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 400 Bad Request - 유효하지 않은 권한 변경
     */
    @ExceptionHandler(InvalidRoleChangeException.class)
    public ResponseEntity<ApiResponse> handleInvalidRoleChangeException(InvalidRoleChangeException e) {
        log.warn("InvalidRoleChangeException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 400 Bad Request - Toss 결제 승인 실패
     */
    @ExceptionHandler(TossPaymentException.class)
    public ResponseEntity<ApiResponse> handleTossPaymentException(TossPaymentException e) {
        log.warn("TossPaymentException: {}", e.getMessage());
        if (e.getStatusCode() == null) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
        return ResponseEntity
                .status(e.getStatusCode())
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 409 Conflict - Duplicate Review
     */
    @ExceptionHandler(com.example.mate.exception.DuplicateReviewException.class)
    public ResponseEntity<ApiResponse> handleDuplicateReviewException(
            com.example.mate.exception.DuplicateReviewException e) {
        log.warn("DuplicateReviewException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }
}
