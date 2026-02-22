package com.example.cheerboard.config;

import com.example.common.exception.RepostNotAllowedException;
import com.example.common.exception.RepostSelfNotAllowedException;
import com.example.common.exception.RepostTargetNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_SELF_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void repostNotAllowed_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_SELF_NOT_ALLOWED_CODE, "자신의 글은 리포스트할 수 없습니다.");
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
        assertThat(body.get("message")).isEqualTo("자신의 글은 리포스트할 수 없습니다.");
    }

    @Test
    void repostTargetNotFound_returnsNotFound_withCodeAndFallback() {
        var ex = new RepostTargetNotFoundException(REPOST_TARGET_NOT_FOUND_CODE, REPOST_TARGET_NOT_FOUND_ERROR);
        var response = handler.handleRepostTargetNotFound(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_TARGET_NOT_FOUND_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_TARGET_NOT_FOUND_ERROR);
    }

    @Test
    void dataIntegrityRepostDuplicate_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_cheer_post_simple_repost\"");
        var response = handler.handleDataIntegrityViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CONFLICT_CODE);
    }

    @Test
    void dataIntegrityOracleUniqueConstraint_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ORA-00001: unique constraint (APP.UQ_CHEER_POST_SIMPLE_REPOST) violated");
        var response = handler.handleDataIntegrityViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CONFLICT_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_CONFLICT_ERROR);
    }

    @Test
    void repostQuoteNotAllowed_canUseCommonRepostHandlerCode() {
        var ex = new RepostNotAllowedException(REPOST_QUOTE_NOT_ALLOWED_CODE, REPOST_QUOTE_NOT_ALLOWED_ERROR);
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_ERROR);
    }

    @Test
    void badRepostCycleFallsBackToRepostNotAllowedDefault() {
        var ex = new RepostNotAllowedException(REPOST_CYCLE_DETECTED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CYCLE_DETECTED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_CYCLE_DETECTED_ERROR);
    }

    @Test
    void dataIntegrityRepostTrackingDuplicate_returnsConflict_withRepostTrackingPattern() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"cheer_post_repost_pkey\"");
        var response = handler.handleDataIntegrityViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CONFLICT_CODE);
    }

    @Test
    void repostCodeWithoutMessageFallsBackToDefault() {
        var ex = new RepostSelfNotAllowedException(REPOST_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_NOT_ALLOWED_CODE);
    }

    @Test
    void cancelRepostNotAllowed_canUseFallbackMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_CANCEL_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_ERROR);
    }

    @Test
    void notARepost_returnsMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_A_REPOST_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo(REPOST_NOT_A_REPOST_CODE);
        assertThat(body.get("message")).isEqualTo(REPOST_NOT_A_REPOST_ERROR);
    }

    @Test
    void noSuchElement_returnsNotFound_withCode() {
        var ex = new java.util.NoSuchElementException("요청한 리소스를 찾을 수 없습니다.");
        var response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo("NOT_FOUND");
        assertThat(body.get("message")).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
    }

    @Test
    void badArgument_returnsBadRequest_withCode() {
        var ex = new IllegalArgumentException("잘못된 요청");
        var response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message")).isEqualTo("잘못된 요청");
    }

    @Test
    void badState_returnsBadRequest_withCode() {
        var ex = new IllegalStateException("잘못된 상태");
        var response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = assertInstanceOf(Map.class, response.getBody());
        assertThat(body.get("code")).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message")).isEqualTo("잘못된 상태");
    }
}
