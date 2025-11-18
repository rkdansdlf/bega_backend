package com.example.demo.exception;

import com.example.demo.dto.ApiResponse;
import com.example.mate.exception.DuplicateApplicationException;
import com.example.mate.exception.DuplicateCheckInException;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.InvalidPartyStatusException;
import com.example.mate.exception.PartyApplicationNotFoundException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.stadium.exception.StadiumNotFoundException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

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
        log.warn("UserNotFoundException: {}", e.getMessage());
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
     * 400 Bad Request - 잘못된 입력값 (Validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
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
}
