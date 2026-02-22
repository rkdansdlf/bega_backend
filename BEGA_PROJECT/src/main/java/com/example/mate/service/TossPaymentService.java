package com.example.mate.service;

import com.example.mate.config.TossPaymentConfig;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.exception.TossPaymentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
public class TossPaymentService {

    private final TossPaymentConfig tossPaymentConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TossPaymentService(TossPaymentConfig tossPaymentConfig, RestClient tossRestClient, ObjectMapper objectMapper) {
        this.tossPaymentConfig = tossPaymentConfig;
        this.restClient = tossRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Toss Payments 결제 승인 요청
     * POST https://api.tosspayments.com/v1/payments/confirm
     * Authorization: Basic {Base64(secretKey:)}  <- 콜론 필수!
     */
    public TossPaymentDTO.ConfirmResponse confirmPayment(
            String paymentKey, String orderId, Integer amount) {

        TossPaymentDTO.ConfirmRequest body = TossPaymentDTO.ConfirmRequest.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .amount(amount)
                .build();

        try {
            TossPaymentDTO.ConfirmResponse response = restClient.post()
                    .uri(tossPaymentConfig.getConfirmUrl())
                    .header("Authorization", buildAuthorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TossPaymentDTO.ConfirmResponse.class);

            if (response == null || response.getPaymentKey() == null || response.getOrderId() == null) {
                throw new TossPaymentException(
                        "결제 승인 응답이 올바르지 않습니다.",
                        HttpStatus.BAD_GATEWAY);
            }

            log.info("[TossPayment] 결제 승인 완료: paymentKey={}, orderId={}, amount={}",
                    paymentKey, orderId, amount);
            return response;

        } catch (RestClientResponseException e) {
            String tossErrorCode = parseTossErrorCode(e.getResponseBodyAsString());
            log.warn("[TossPayment] 결제 승인 실패: status={}, code={}, body={}",
                    e.getStatusCode(), tossErrorCode, e.getResponseBodyAsString());
            throw new TossPaymentException(
                    "결제 승인에 실패했습니다: " + e.getStatusCode(), e.getStatusCode(), tossErrorCode);
        } catch (TossPaymentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TossPayment] 결제 승인 중 예기치 않은 오류 발생", e);
            throw new TossPaymentException(
                    "결제 승인 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Toss Payments 결제 취소 요청
     * POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel
     */
    public TossPaymentDTO.CancelResponse cancelPayment(String paymentKey, String cancelReason, Integer cancelAmount) {
        TossPaymentDTO.CancelRequest body = TossPaymentDTO.CancelRequest.builder()
                .cancelReason(cancelReason)
                .cancelAmount(cancelAmount)
                .build();

        try {
            TossPaymentDTO.CancelResponse response = restClient.post()
                    .uri(tossPaymentConfig.getCancelUrl(), paymentKey)
                    .header("Authorization", buildAuthorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TossPaymentDTO.CancelResponse.class);

            log.info("[TossPayment] 결제 취소 완료: paymentKey={}, cancelAmount={}", paymentKey, cancelAmount);
            return response;
        } catch (RestClientResponseException e) {
            String tossErrorCode = parseTossErrorCode(e.getResponseBodyAsString());
            log.warn("[TossPayment] 결제 취소 실패: paymentKey={}, status={}, code={}, body={}",
                    paymentKey, e.getStatusCode(), tossErrorCode, e.getResponseBodyAsString());
            throw new TossPaymentException(
                    "결제 취소에 실패했습니다: " + e.getStatusCode(), e.getStatusCode(), tossErrorCode);
        } catch (Exception e) {
            log.error("[TossPayment] 결제 취소 중 예기치 않은 오류 발생", e);
            throw new TossPaymentException(
                    "결제 취소 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public TossPaymentDTO.ConfirmResponse getPayment(String paymentKey) {
        try {
            return restClient.get()
                    .uri("https://api.tosspayments.com/v1/payments/{paymentKey}", paymentKey)
                    .header("Authorization", buildAuthorizationHeader())
                    .retrieve()
                    .body(TossPaymentDTO.ConfirmResponse.class);
        } catch (RestClientResponseException e) {
            String tossErrorCode = parseTossErrorCode(e.getResponseBodyAsString());
            throw new TossPaymentException(
                    "결제 조회에 실패했습니다: " + e.getStatusCode(), e.getStatusCode(), tossErrorCode);
        } catch (Exception e) {
            throw new TossPaymentException(
                    "결제 조회 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Toss API 오류 응답 body에서 에러 코드를 추출합니다.
     * Toss API 오류 응답 형식: {"code": "ALREADY_CANCELED_PAYMENT", "message": "..."}
     */
    private String parseTossErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            TossPaymentDTO.ErrorResponse errorResponse = objectMapper.readValue(responseBody, TossPaymentDTO.ErrorResponse.class);
            return errorResponse.getCode();
        } catch (Exception e) {
            log.debug("[TossPayment] 에러 응답 파싱 실패: {}", responseBody);
            return null;
        }
    }

    private String buildAuthorizationHeader() {
        String credentials = tossPaymentConfig.getSecretKey() + ":";
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
