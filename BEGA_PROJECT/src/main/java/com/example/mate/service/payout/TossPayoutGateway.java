package com.example.mate.service.payout;

import com.example.mate.config.TossPayoutConfig;
import com.example.mate.dto.TossPaymentDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "payment.payout", name = "provider", havingValue = "TOSS")
@RequiredArgsConstructor
public class TossPayoutGateway implements PayoutGateway {

    private final TossPayoutConfig tossPayoutConfig;
    @Qualifier("tossPayoutRestClient")
    private final RestClient tossPayoutRestClient;
    private final TossPayoutEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderCode() {
        return "TOSS";
    }

    @Override
    public PayoutResult requestPayout(PayoutRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sellerId", request.providerSellerId());
        payload.put("amount", request.amount());
        payload.put("currency", request.currency());
        payload.put("orderId", request.orderId());
        payload.put("paymentTransactionId", request.paymentTransactionId());

        Object requestBody = buildRequestBody(payload);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = tossPayoutRestClient.post()
                    .uri(resolveUrl(tossPayoutConfig.getRequestPath()))
                    .header("TossPayments-Api-Secret", tossPayoutConfig.getApiSecret())
                    .header("TossPayments-api-security-mode", resolveSecurityMode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new PayoutGatewayException("TOSS payout 응답이 비어 있습니다.", "TOSS_PAYOUT_EMPTY_RESPONSE", null);
            }
            String providerRef = extractProviderRef(response);
            return new PayoutResult(providerRef, String.valueOf(response.getOrDefault("status", "REQUESTED")));
        } catch (RestClientResponseException e) {
            throw new PayoutGatewayException(
                    "TOSS payout 요청에 실패했습니다: " + e.getStatusCode(),
                    parseFailureCode(e.getResponseBodyAsString()),
                    e.getStatusCode());
        } catch (PayoutGatewayException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PayoutGatewayException(
                    "TOSS payout 요청 중 오류가 발생했습니다.",
                    "TOSS_PAYOUT_REQUEST_FAILED",
                    null);
        }
    }

    @Override
    public PayoutStatusResult getPayoutStatus(String providerRef) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = tossPayoutRestClient.get()
                    .uri(resolveUrl(tossPayoutConfig.getStatusPath()), providerRef)
                    .header("TossPayments-Api-Secret", tossPayoutConfig.getApiSecret())
                    .header("TossPayments-api-security-mode", resolveSecurityMode())
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return new PayoutStatusResult(providerRef, "UNKNOWN", "TOSS_PAYOUT_EMPTY_RESPONSE", "응답이 비어 있습니다.");
            }

            String status = String.valueOf(response.getOrDefault("status", "UNKNOWN"));
            return new PayoutStatusResult(providerRef, status, null, null);
        } catch (RestClientResponseException e) {
            return new PayoutStatusResult(
                    providerRef,
                    "FAILED",
                    parseFailureCode(e.getResponseBodyAsString()),
                    e.getResponseBodyAsString());
        }
    }

    @Override
    public SellerRegistrationResult registerSeller(SellerRegistrationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sellerId", request.providerSellerId());
        payload.put("kycStatus", request.kycStatus());
        payload.put("metadata", request.metadataJson());

        Object requestBody = buildRequestBody(payload);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = tossPayoutRestClient.post()
                    .uri(resolveUrl(tossPayoutConfig.getSellerRegisterPath()))
                    .header("TossPayments-Api-Secret", tossPayoutConfig.getApiSecret())
                    .header("TossPayments-api-security-mode", resolveSecurityMode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String status = response == null ? "REGISTERED" : String.valueOf(response.getOrDefault("status", "REGISTERED"));
            return new SellerRegistrationResult(request.providerSellerId(), status);
        } catch (RestClientResponseException e) {
            throw new PayoutGatewayException(
                    "TOSS seller 등록에 실패했습니다: " + e.getStatusCode(),
                    parseFailureCode(e.getResponseBodyAsString()),
                    e.getStatusCode());
        }
    }

    private Object buildRequestBody(Map<String, Object> payload) {
        if ("ENCRYPTION".equals(resolveSecurityMode())) {
            String encryptedPayload = encryptionService.encryptPayload(
                    payload,
                    tossPayoutConfig.getEncryptionPublicKey(),
                    tossPayoutConfig.getEncryptionPublicKeyPath());
            Map<String, String> encryptedBody = new HashMap<>();
            encryptedBody.put("payload", encryptedPayload);
            return encryptedBody;
        }
        return payload;
    }

    private String resolveSecurityMode() {
        String securityMode = tossPayoutConfig.getSecurityMode();
        if (securityMode == null || securityMode.isBlank()) {
            return "ENCRYPTION";
        }
        return securityMode.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveUrl(String path) {
        String baseUrl = tossPayoutConfig.getBaseUrl() == null ? "" : tossPayoutConfig.getBaseUrl().trim();
        String normalizedPath = path == null ? "" : path.trim();
        if (baseUrl.endsWith("/") && normalizedPath.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + normalizedPath;
        }
        if (!baseUrl.endsWith("/") && !normalizedPath.startsWith("/")) {
            return baseUrl + "/" + normalizedPath;
        }
        return baseUrl + normalizedPath;
    }

    private String parseFailureCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "TOSS_PAYOUT_REQUEST_FAILED";
        }
        try {
            TossPaymentDTO.ErrorResponse errorResponse = objectMapper.readValue(responseBody, TossPaymentDTO.ErrorResponse.class);
            return errorResponse.getCode() != null ? errorResponse.getCode() : "TOSS_PAYOUT_REQUEST_FAILED";
        } catch (Exception ignored) {
            return "TOSS_PAYOUT_REQUEST_FAILED";
        }
    }

    private String extractProviderRef(Map<String, Object> response) {
        Object payoutId = response.get("payoutId");
        if (payoutId != null) {
            return String.valueOf(payoutId);
        }
        Object id = response.get("id");
        if (id != null) {
            return String.valueOf(id);
        }
        Object payoutKey = response.get("payoutKey");
        if (payoutKey != null) {
            return String.valueOf(payoutKey);
        }
        Object providerRef = response.get("providerRef");
        if (providerRef != null) {
            return String.valueOf(providerRef);
        }
        throw new PayoutGatewayException("TOSS payout 응답에 providerRef가 없습니다.", "TOSS_PAYOUT_NO_PROVIDER_REF", HttpStatusCode.valueOf(502));
    }
}
