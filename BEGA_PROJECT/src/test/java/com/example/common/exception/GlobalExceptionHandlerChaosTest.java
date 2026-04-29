package com.example.common.exception;

import com.example.common.dto.ApiResponse;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Chaos tests: 인프라/장애 예외가 GlobalExceptionHandler에서 올바른 HTTP 상태와
 * 한국어 메시지로 변환되고, 내부 오류 메시지가 외부에 노출되지 않는지 검증한다.
 */
class GlobalExceptionHandlerChaosTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ─────────────────────────────────────────────
    // DB 장애 시나리오
    // ─────────────────────────────────────────────

    @Test
    void transientDataAccess_returns503_withKoreanMessage() {
        var ex = new TransientDataAccessResourceException("Connection pool exhausted");

        var response = handler.handleTransientDataAccessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("서버가 현재 혼잡합니다. 잠시 후 다시 시도해주세요.");
        // 내부 오류 메시지가 응답에 노출되면 안 된다
        assertThat(body.getMessage()).doesNotContain("Connection pool exhausted");
    }

    @Test
    void queryTimeout_returns503_asTransientException() {
        var ex = new QueryTimeoutException("DB query timed out after 30s");

        var response = handler.handleTransientDataAccessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getMessage()).isEqualTo("서버가 현재 혼잡합니다. 잠시 후 다시 시도해주세요.");
        assertThat(body.getMessage()).doesNotContain("30s");
    }

    @Test
    void cannotCreateTransaction_returns503_asTemporaryDatabaseError() {
        var ex = new CannotCreateTransactionException("ORA-12506 listener rejected connection");

        var response = handler.handleDatabaseConnectionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo("TEMPORARY_DATABASE_ERROR");
        assertThat(body.getMessage()).isEqualTo("서버가 현재 혼잡합니다. 잠시 후 다시 시도해주세요.");
        assertThat(body.getMessage()).doesNotContain("ORA-12506");
        assertThat(body.getMessage()).doesNotContain("listener");
    }

    // ─────────────────────────────────────────────
    // 레이트 리밋 시나리오
    // ─────────────────────────────────────────────

    @Test
    void rateLimitExceeded_returns429_withKoreanMessage() {
        var ex = new RateLimitExceededException("너무 많은 요청을 보냈습니다. 잠시 후 다시 시도해주세요.");

        var response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("너무 많은 요청을 보냈습니다. 잠시 후 다시 시도해주세요.");
    }

    @Test
    void refreshTokenRevokeFailed_returns503_withoutOverstatingRevocation() {
        var ex = new RefreshTokenRevokeFailedException(new RuntimeException("delete failed"));

        var response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getCode()).isEqualTo("REFRESH_TOKEN_REVOKE_FAILED");
        assertThat(body.getMessage()).isEqualTo("보안 조치를 완료할 수 없습니다. 잠시 후 다시 시도해주세요.");
        assertThat(body.getMessage()).doesNotContain("모든 세션이 종료되었습니다");
        assertThat(body.getMessage()).doesNotContain("delete failed");
    }

    // ─────────────────────────────────────────────
    // AI 서비스 장애 시나리오 (502 / 504)
    // ─────────────────────────────────────────────

    @Test
    void aiGatewayTimeout_504_propagatesCorrectly() {
        var ex = new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI upstream request timed out");

        var response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getMessage()).isEqualTo("AI upstream request timed out");
    }

    @Test
    void aiConnectionFailed_502_propagatesCorrectly() {
        var ex = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI upstream connection failed");

        var response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getMessage()).isEqualTo("AI upstream connection failed");
    }

    @Test
    void aiServiceUnavailable_503_propagatesCorrectly() {
        var ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service URL is not configured");

        var response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getMessage()).isEqualTo("AI service URL is not configured");
    }

    @Test
    void responseStatusException_nullReason_fallsBackToHttpStatusPhrase() {
        var ex = new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, null);

        var response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        // reason이 null이면 HttpStatus.getReasonPhrase()로 폴백
        assertThat(body.getMessage()).isEqualTo("Unprocessable Entity");
    }

    // ─────────────────────────────────────────────
    // 예상치 못한 예외 - 내부 메시지 노출 방지 (보안)
    // ─────────────────────────────────────────────

    @Test
    void unexpectedRuntime_returns500_doesNotLeakInternalMessage() {
        var ex = new RuntimeException("null pointer in UserService.getProfile() line 42");

        var response = handler.handleGlobalException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        // 내부 구현 세부 정보가 절대 응답에 포함되면 안 된다 (보안)
        assertThat(body.getMessage()).doesNotContain("UserService");
        assertThat(body.getMessage()).doesNotContain("line 42");
        assertThat(body.getMessage()).doesNotContain("null pointer");
    }

    @Test
    void nullPointerException_returns500_doesNotLeakClassName() {
        var ex = new NullPointerException();

        var response = handler.handleGlobalException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        var body = assertInstanceOf(ApiResponse.class, response.getBody());
        assertThat(body.getMessage()).isEqualTo("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        assertThat(body.getMessage()).doesNotContain("NullPointerException");
    }
}
