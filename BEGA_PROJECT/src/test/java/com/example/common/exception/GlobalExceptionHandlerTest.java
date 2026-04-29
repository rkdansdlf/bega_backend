package com.example.common.exception;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.common.dto.ApiResponse;
import com.example.mate.exception.TossPaymentException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void repostNotAllowed_returnsForbidden_withCodeAndDefaultMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_SELF_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_NOT_ALLOWED_SELF_ERROR);
    }

    @Test
    void repostBlocked_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_ALLOWED_BLOCKED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_NOT_ALLOWED_BLOCKED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_NOT_ALLOWED_BLOCKED_ERROR);
    }

    @Test
    void repostPrivate_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_ALLOWED_PRIVATE_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_ERROR);
    }

    @Test
    void repostCancelNotAllowed_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_CANCEL_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_ERROR);
    }

    @Test
    void repostNotARepost_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_A_REPOST_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_NOT_A_REPOST_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_NOT_A_REPOST_ERROR);
    }

    @Test
    void repostQuoteNotAllowed_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_QUOTE_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_ERROR);
    }

    @Test
    void repostTargetNotFound_returnsNotFound_withCodeAndMessage() {
        var ex = new RepostTargetNotFoundException(REPOST_TARGET_NOT_FOUND_CODE, null);
        var response = handler.handleRepostTargetNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_TARGET_NOT_FOUND_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_TARGET_NOT_FOUND_ERROR);
    }

    @Test
    void repostDataIntegrityDuplicate_returnsConflict_withRepostErrorCode() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_cheer_post_simple_repost\"");
        var response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CONFLICT_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_CONFLICT_ERROR);
    }

    @Test
    void repostDataIntegrityOracleUniqueConstraint_returnsConflict_withRepostErrorCode() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ORA-00001: unique constraint (APP.UQ_CHEER_POST_SIMPLE_REPOST) violated");
        var response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CONFLICT_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_CONFLICT_ERROR);
    }

    @Test
    void repostCyclePolicyNoMessageFallsBackToDefaultError() {
        var ex = new RepostNotAllowedException(REPOST_CYCLE_DETECTED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CYCLE_DETECTED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_CYCLE_DETECTED_ERROR);
    }

    @Test
    void policyViolationWithoutCodeFallsBackToGenericRepostCode() {
        var ex = new RepostNotAllowedException(REPOST_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_NOT_ALLOWED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_NOT_ALLOWED_ERROR);
    }

    @Test
    void bindException_returnsValidationErrorsMap() {
        BindException ex = new BindException(new Object(), "signupDto");
        ex.addError(new FieldError("signupDto", "email", "유효하지 않은 이메일 형식입니다."));
        ex.addError(new FieldError("signupDto", "confirmPassword", "비밀번호와 비밀번호 확인이 일치하지 않습니다."));

        var response = handler.handleBindException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getErrors()).isEqualTo(Map.of(
                "email", "유효하지 않은 이메일 형식입니다.",
                "confirmPassword", "비밀번호와 비밀번호 확인이 일치하지 않습니다."));
    }

    @Test
    void noSuchElement_returnsNotFound() {
        var ex = new java.util.NoSuchElementException("요청한 데이터를 찾을 수 없습니다.");
        var response = handler.handleNoSuchElementException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo("NOT_FOUND");
        assertThat(body.getMessage()).isEqualTo("요청한 데이터를 찾을 수 없습니다.");
        assertThat(body.getData()).isNull();
    }

    @Test
    void illegalArgument_returnsBadRequest() {
        var ex = new IllegalArgumentException("잘못된 입력입니다.");
        var response = handler.handleIllegalArgumentException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(body.getMessage()).isEqualTo("잘못된 입력입니다.");
    }

    @Test
    void illegalState_returnsBadRequest() {
        var ex = new IllegalStateException("요청 상태가 올바르지 않습니다.");
        var response = handler.handleIllegalStateException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo("BAD_REQUEST");
        // [Security] IllegalStateException은 내부 상태 오류 메시지가 노출되지 않도록 일반 메시지로 대체됨
        assertThat(body.getMessage()).isEqualTo("요청을 처리할 수 없습니다.");
    }

    @Test
    void tossPaymentException_preservesHttpStatus() {
        var ex = new TossPaymentException("PAYMENT_CONFIRM_FAILED", "결제 승인 실패", HttpStatus.UNAUTHORIZED);
        var response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo("PAYMENT_CONFIRM_FAILED");
        assertThat(body.getMessage()).isEqualTo("결제 승인 실패");
    }

    @Test
    void responseStatusException_preservesOriginalStatus() {
        var ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service URL is not configured");
        var response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(body.getMessage()).isEqualTo("AI service URL is not configured");
    }

    @Test
    void accessDenied_returnsForbidden() {
        var ex = new AccessDeniedException("본인의 일기만 조회할 수 있습니다.");
        var response = handler.handleAccessDeniedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo("FORBIDDEN");
        assertThat(body.getMessage()).isEqualTo("본인의 일기만 조회할 수 있습니다.");
    }
}
