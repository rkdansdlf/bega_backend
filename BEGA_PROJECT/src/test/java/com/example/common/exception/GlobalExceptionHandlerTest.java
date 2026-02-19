package com.example.common.exception;

import com.example.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_SELF_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_SELF_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void repostNotAllowed_returnsForbidden_withCodeAndDefaultMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_SELF_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_NOT_ALLOWED_SELF_ERROR);
    }

    @Test
    void repostBlocked_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_ALLOWED_BLOCKED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_NOT_ALLOWED_BLOCKED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_NOT_ALLOWED_BLOCKED_ERROR);
    }

    @Test
    void repostPrivate_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_ALLOWED_PRIVATE_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_ERROR);
    }

    @Test
    void repostCancelNotAllowed_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_CANCEL_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_ERROR);
    }

    @Test
    void repostNotARepost_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_A_REPOST_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_NOT_A_REPOST_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_NOT_A_REPOST_ERROR);
    }

    @Test
    void repostQuoteNotAllowed_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostNotAllowedException(REPOST_QUOTE_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_ERROR);
    }

    @Test
    void repostTargetNotFound_returnsNotFound_withCodeAndMessage() {
        var ex = new RepostTargetNotFoundException(REPOST_TARGET_NOT_FOUND_CODE, null);
        var response = handler.handleRepostTargetNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_TARGET_NOT_FOUND_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_TARGET_NOT_FOUND_ERROR);
    }

    @Test
    void repostDataIntegrityDuplicate_returnsConflict_withRepostErrorCode() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_cheer_post_simple_repost\"");
        var response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CONFLICT_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_CONFLICT_ERROR);
    }

    @Test
    void repostDataIntegrityOracleUniqueConstraint_returnsConflict_withRepostErrorCode() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ORA-00001: unique constraint (APP.UQ_CHEER_POST_SIMPLE_REPOST) violated");
        var response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CONFLICT_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_CONFLICT_ERROR);
    }

    @Test
    void repostCyclePolicyNoMessageFallsBackToDefaultError() {
        var ex = new RepostNotAllowedException(REPOST_CYCLE_DETECTED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CYCLE_DETECTED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_CYCLE_DETECTED_ERROR);
    }

    @Test
    void policyViolationWithoutCodeFallsBackToGenericRepostCode() {
        var ex = new RepostNotAllowedException(REPOST_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_NOT_ALLOWED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_NOT_ALLOWED_ERROR);
    }

    @Test
    void noSuchElement_returnsNotFound() {
        var ex = new java.util.NoSuchElementException("요청한 데이터를 찾을 수 없습니다.");
        var response = handler.handleNoSuchElementException(ex);

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("요청한 데이터를 찾을 수 없습니다.");
        assertThat(body.getData()).isNull();
    }

    @Test
    void illegalArgument_returnsBadRequest() {
        var ex = new IllegalArgumentException("잘못된 입력입니다.");
        var response = handler.handleIllegalArgumentException(ex);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("잘못된 입력입니다.");
        assertThat(body.getData()).isNull();
    }

    @Test
    void illegalState_returnsBadRequest() {
        var ex = new IllegalStateException("요청 상태가 올바르지 않습니다.");
        var response = handler.handleIllegalStateException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("요청 상태가 올바르지 않습니다.");
        assertThat(body.getData()).isNull();
    }
}
