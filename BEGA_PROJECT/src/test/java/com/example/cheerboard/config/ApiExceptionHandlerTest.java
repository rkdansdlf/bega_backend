package com.example.cheerboard.config;

import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CONFLICT_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.DUPLICATE_COMMENT_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.DUPLICATE_COMMENT_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_SELF_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.cheerboard.exception.DuplicateCommentException;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.RepostNotAllowedException;
import com.example.common.exception.RepostSelfNotAllowedException;
import com.example.common.exception.RepostTargetNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void repostNotAllowed_returnsForbidden_withCodeAndMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_SELF_NOT_ALLOWED_CODE, "자신의 글은 리포스트할 수 없습니다.");
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
        assertThat(body.getMessage()).isEqualTo("자신의 글은 리포스트할 수 없습니다.");
    }

    @Test
    void repostTargetNotFound_returnsNotFound_withCodeAndFallback() {
        var ex = new RepostTargetNotFoundException(REPOST_TARGET_NOT_FOUND_CODE, REPOST_TARGET_NOT_FOUND_ERROR);
        var response = handler.handleRepostTargetNotFound(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_TARGET_NOT_FOUND_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_TARGET_NOT_FOUND_ERROR);
    }

    @Test
    void dataIntegrityRepostDuplicate_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_cheer_post_simple_repost\"");
        var response = handler.handleDataIntegrityViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CONFLICT_CODE);
    }

    @Test
    void dataIntegrityOracleUniqueConstraint_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ORA-00001: unique constraint (APP.UQ_CHEER_POST_SIMPLE_REPOST) violated");
        var response = handler.handleDataIntegrityViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CONFLICT_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_CONFLICT_ERROR);
    }

    @Test
    void repostQuoteNotAllowed_canUseCommonRepostHandlerCode() {
        var ex = new RepostNotAllowedException(REPOST_QUOTE_NOT_ALLOWED_CODE, REPOST_QUOTE_NOT_ALLOWED_ERROR);
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_ERROR);
    }

    @Test
    void badRepostCycleFallsBackToRepostNotAllowedDefault() {
        var ex = new RepostNotAllowedException(REPOST_CYCLE_DETECTED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CYCLE_DETECTED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_CYCLE_DETECTED_ERROR);
    }

    @Test
    void dataIntegrityRepostTrackingDuplicate_returnsConflict_withRepostTrackingPattern() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"cheer_post_repost_pkey\"");
        var response = handler.handleDataIntegrityViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CONFLICT_CODE);
    }

    @Test
    void repostCodeWithoutMessageFallsBackToDefault() {
        var ex = new RepostSelfNotAllowedException(REPOST_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_NOT_ALLOWED_CODE);
    }

    @Test
    void duplicateComment_returnsConflict() {
        var response = handler.handleDuplicateComment(new DuplicateCommentException(DUPLICATE_COMMENT_ERROR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(DUPLICATE_COMMENT_CODE);
        assertThat(body.getMessage()).isEqualTo(DUPLICATE_COMMENT_ERROR);
    }

    @Test
    void cancelRepostNotAllowed_canUseFallbackMessage() {
        var ex = new RepostSelfNotAllowedException(REPOST_CANCEL_NOT_ALLOWED_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_ERROR);
    }

    @Test
    void notARepost_returnsMessage() {
        var ex = new RepostNotAllowedException(REPOST_NOT_A_REPOST_CODE, null);
        var response = handler.handleRepostNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getCode()).isEqualTo(REPOST_NOT_A_REPOST_CODE);
        assertThat(body.getMessage()).isEqualTo(REPOST_NOT_A_REPOST_ERROR);
    }
}
