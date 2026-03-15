package com.example.bega.auth.service;

import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.bega.auth.dto.OAuth2LinkTicketData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 계정 연동 상태 관리 서비스
 * Redis를 사용하여 state 파라미터와 연동 정보를 매핑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2LinkStateService {

    public static final String FAILURE_MISSING_LINK_TOKEN = "oauth2_link_failed";
    public static final String FAILURE_INVALID_LINK_TOKEN = "oauth2_link_failed";
    public static final String FAILURE_EXPIRED_LINK_TOKEN = "oauth2_link_session_expired";
    public static final String FAILURE_REPLAYED_LINK_TOKEN = "oauth2_link_replayed";

    private static final String STATE_PREFIX = "oauth2:link:state:";
    private static final String TICKET_PREFIX = "oauth2:link:ticket:";
    private static final Duration TTL = Duration.ofMinutes(5);
    public static final String ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE = "oauth2_link_state_store_unavailable";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final com.example.auth.service.AuthSecurityMonitoringService securityMonitoringService;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issueLinkToken(Long userId) {
        String ticket = generateOpaqueTicket();
        try {
            OAuth2LinkTicketData data = OAuth2LinkTicketData.issued(userId);
            redisTemplate.opsForValue().set(
                    ticketKey(ticket),
                    objectMapper.writeValueAsString(data),
                    TTL.toMillis(),
                    TimeUnit.MILLISECONDS);
            log.info("Issued OAuth2 link ticket for userId={}", userId);
            return ticket;
        } catch (JsonProcessingException e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to serialize OAuth2 link ticket data for userId={}", userId, e);
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        } catch (Exception e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to persist OAuth2 link ticket for userId={}", userId, e);
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        }
    }

    public LinkTicketConsumeResult consumeLinkToken(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return LinkTicketConsumeResult.failure(FAILURE_MISSING_LINK_TOKEN);
        }

        String key = ticketKey(ticket);
        String json;
        try {
            json = redisTemplate.opsForValue().getAndDelete(key);
        } catch (Exception e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to consume OAuth2 link ticket", e);
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        }

        if (json == null) {
            securityMonitoringService.recordOAuth2LinkReject();
            return LinkTicketConsumeResult.failure(FAILURE_INVALID_LINK_TOKEN);
        }

        try {
            OAuth2LinkTicketData data = objectMapper.readValue(json, OAuth2LinkTicketData.class);
            long remainingTtlMillis = data.remainingTtlMillis();

            if (data.consumed()) {
                restoreConsumedMarker(key, data, remainingTtlMillis);
                securityMonitoringService.recordOAuth2LinkReject();
                return LinkTicketConsumeResult.failure(FAILURE_REPLAYED_LINK_TOKEN);
            }

            if (data.userId() == null || remainingTtlMillis <= 0) {
                securityMonitoringService.recordOAuth2LinkReject();
                return LinkTicketConsumeResult.failure(FAILURE_EXPIRED_LINK_TOKEN);
            }

            restoreConsumedMarker(key, data.consumedMarker(), remainingTtlMillis);
            return LinkTicketConsumeResult.success(data.userId());
        } catch (JsonProcessingException e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to deserialize OAuth2 link ticket data", e);
            return LinkTicketConsumeResult.failure(FAILURE_INVALID_LINK_TOKEN);
        } catch (Exception e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to process OAuth2 link ticket data", e);
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        }
    }

    /**
     * 연동 상태 저장 (원본 OAuth2 state를 key로 사용)
     *
     * @param state OAuth2 원본 state 값
     * @param data  연동 상태 데이터
     */
    public void saveLinkByState(String state, OAuth2LinkStateData data) {
        try {
            String key = stateKey(state);
            String json = objectMapper.writeValueAsString(data);

            redisTemplate.opsForValue().set(key, json, TTL.toMillis(), TimeUnit.MILLISECONDS);

            log.info("Saved OAuth2 link state for userId={}, failureReason={}",
                    data.userId(), data.failureReason());
        } catch (JsonProcessingException e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to serialize OAuth2LinkStateData", e);
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        } catch (Exception e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to persist OAuth2 link state", e);
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        }
    }

    /**
     * 연동 상태 조회 및 삭제 (원본 OAuth2 state로 조회)
     *
     * @param state OAuth2 원본 state 값
     * @return 연동 상태 데이터 (없으면 null - 일반 로그인)
     */
    public OAuth2LinkStateData consumeLinkByState(String state) {
        if (state == null || state.isEmpty()) {
            return null;
        }

        try {
            String key = stateKey(state);
            String json = redisTemplate.opsForValue().getAndDelete(key);

            if (json == null) {
                // 일반 로그인 (연동 모드 아님) - 정상 케이스
                log.debug("No link state found for incoming OAuth2 state");
                return null;
            }

            OAuth2LinkStateData data = objectMapper.readValue(json, OAuth2LinkStateData.class);

            // 만료 확인
            if (data.isExpired()) {
                securityMonitoringService.recordOAuth2LinkReject();
                log.warn("OAuth2 link state expired: createdAt={}", data.createdAt());
                return null;
            }

            log.info("Consumed OAuth2 link state for userId={}, failureReason={}",
                    data.userId(), data.failureReason());

            return data;
        } catch (JsonProcessingException e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to deserialize OAuth2LinkStateData", e);
            return null;
        } catch (Exception e) {
            securityMonitoringService.recordOAuth2LinkReject();
            log.error("Failed to consume OAuth2LinkStateData", e);
            return null;
        }
    }

    private void restoreConsumedMarker(String key, OAuth2LinkTicketData data, long remainingTtlMillis) {
        long ttlMillis = Math.max(1L, remainingTtlMillis);
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(data),
                    ttlMillis,
                    TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException e) {
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        }
    }

    private String ticketKey(String ticket) {
        return TICKET_PREFIX + sha256(ticket);
    }

    private String stateKey(String state) {
        return STATE_PREFIX + state;
    }

    private String generateOpaqueTicket() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new OAuth2LinkStateStoreException(ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE, e);
        }
    }

    public record LinkTicketConsumeResult(Long userId, String failureReason) {
        public static LinkTicketConsumeResult success(Long userId) {
            return new LinkTicketConsumeResult(userId, null);
        }

        public static LinkTicketConsumeResult failure(String failureReason) {
            return new LinkTicketConsumeResult(null, failureReason);
        }

        public boolean isSuccess() {
            return userId != null && failureReason == null;
        }
    }

    public static class OAuth2LinkStateStoreException extends RuntimeException {
        public OAuth2LinkStateStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
