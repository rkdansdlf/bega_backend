package com.example.auth.service;

import com.example.auth.dto.OAuth2StateData;
import com.example.auth.dto.OAuth2StateStorageData;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * OAuth2 인증 상태 관리 서비스
 * [Security Fix] Redis에 userId만 저장하여 민감 정보 노출 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2StateService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final com.example.auth.service.AuthSecurityMonitoringService securityMonitoringService;

    private static final String PREFIX = "oauth2:state:";
    private static final String REPLAY_PREFIX = "oauth2:state-replay:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration REPLAY_TTL = Duration.ofSeconds(60);
    private static final DefaultRedisScript<String> CONSUME_STATE_SCRIPT = new DefaultRedisScript<>("""
            local state = redis.call('GET', KEYS[1])
            if state then
                redis.call('RENAME', KEYS[1], KEYS[2])
                redis.call('EXPIRE', KEYS[2], ARGV[1])
                return 'C:' .. state
            end
            local replay = redis.call('GET', KEYS[2])
            if replay then
                return 'R:' .. replay
            end
            return false
            """, String.class);
    public static final String ERROR_CODE_STATE_STORE_UNAVAILABLE = "oauth2_state_store_unavailable";

    /**
     * OAuth2 인증 상태 저장 (userId만 Redis에 저장)
     * 
     * @param userId 사용자 ID
     * @return 생성된 stateId
     */
    public String saveState(Long userId) {
        String stateId = UUID.randomUUID().toString();
        try {
            OAuth2StateStorageData storageData = OAuth2StateStorageData.of(userId);
            String json = objectMapper.writeValueAsString(storageData);
            redisTemplate.opsForValue().set(PREFIX + stateId, json, TTL);
            log.info("OAuth2 state saved: userId={}", userId);
        } catch (JsonProcessingException e) {
            securityMonitoringService.recordOAuth2StateReject();
            log.error("Failed to serialize OAuth2 state data: userId={}", userId, e);
            throw new OAuth2StateStoreException(ERROR_CODE_STATE_STORE_UNAVAILABLE, e);
        } catch (Exception e) {
            securityMonitoringService.recordOAuth2StateReject();
            log.error("Failed to persist OAuth2 state: userId={}, errorType={}",
                    userId, e.getClass().getSimpleName());
            throw new OAuth2StateStoreException(ERROR_CODE_STATE_STORE_UNAVAILABLE, e);
        }
        return stateId;
    }

    /**
     * OAuth2 인증 상태 소비 (일회성, DB에서 사용자 정보 조회)
     * 
     * @param stateId state ID
     * @return 사용자 정보 (없으면 null)
     */
    public OAuth2StateData consumeState(String stateId) {
        return consumeState(stateId, null);
    }

    /**
     * OAuth2 callback 응답 유실 시 state 소유자만 60초 안에 결과를 다시 조회한다.
     * 기존 단일 인자 진입점은 재조회 권한이 없으므로 기존 one-time 소비 동작을 유지한다.
     */
    public OAuth2StateData consumeState(String stateId, Long authenticatedUserId) {
        String key = PREFIX + stateId;
        String stateResult;
        try {
            stateResult = redisTemplate.execute(
                    CONSUME_STATE_SCRIPT,
                    List.of(key, REPLAY_PREFIX + stateId),
                    String.valueOf(REPLAY_TTL.toSeconds()));
        } catch (Exception e) {
            log.error("Failed to consume OAuth2 state from Redis: errorType={}", e.getClass().getSimpleName());
            securityMonitoringService.recordOAuth2StateReject();
            return null;
        }
        if (stateResult == null || stateResult.length() <= 2) {
            log.warn("OAuth2 state not found or already consumed");
            securityMonitoringService.recordOAuth2StateReject();
            return null;
        }
        boolean replay = stateResult.startsWith("R:");
        if (!replay && !stateResult.startsWith("C:")) {
            securityMonitoringService.recordOAuth2StateReject();
            return null;
        }
        String json = stateResult.substring(2);

        try {
            OAuth2StateStorageData storageData = objectMapper.readValue(json, OAuth2StateStorageData.class);
            if (storageData.isExpired()) {
                log.warn("OAuth2 state expired: createdAt={}", storageData.createdAt());
                securityMonitoringService.recordOAuth2StateReject();
                return null;
            }

            Long userId = storageData.userId();
            if (userId == null) {
                log.warn("OAuth2 state has null userId");
                securityMonitoringService.recordOAuth2StateReject();
                return null;
            }
            if (replay && !Objects.equals(authenticatedUserId, userId)) {
                securityMonitoringService.recordOAuth2StateReject();
                return null;
            }

            Optional<UserEntity> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found for OAuth2 state: userId={}", userId);
                securityMonitoringService.recordOAuth2StateReject();
                return null;
            }

            UserEntity user = userOpt.get();
            String favoriteTeamId = user.getFavoriteTeamId();
            if (favoriteTeamId == null || favoriteTeamId.isEmpty()) {
                favoriteTeamId = "없음";
            }
            OAuth2StateData result = new OAuth2StateData(
                    Objects.requireNonNull(user.getEmail()),
                    Objects.requireNonNull(user.getName()),
                    Objects.requireNonNull(user.getRole()),
                    user.getProfileImageUrl(),
                    favoriteTeamId,
                    user.getHandle());

            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize OAuth2 state data", e);
            securityMonitoringService.recordOAuth2StateReject();
            return null;
        } catch (Exception e) {
            log.error("Failed to process OAuth2 state", e);
            securityMonitoringService.recordOAuth2StateReject();
            return null;
        }
    }

    /**
     * 저장된 userId만 조회 (state 삭제하지 않음)
     * 
     * @param stateId state ID
     * @return userId (없으면 null)
     */
    public Long peekUserId(String stateId) {
        String key = PREFIX + stateId;
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Failed to peek OAuth2 state in Redis: errorType={}", e.getClass().getSimpleName());
            return null;
        }
        if (json == null) {
            return null;
        }
        try {
            OAuth2StateStorageData storageData = objectMapper.readValue(json, OAuth2StateStorageData.class);
            return storageData.userId();
        } catch (JsonProcessingException e) {
            log.error("Failed to peek OAuth2 state", e);
            return null;
        } catch (Exception e) {
            log.error("Failed to process peek OAuth2 state", e);
            return null;
        }
    }

    public static class OAuth2StateStoreException extends RuntimeException {
        public OAuth2StateStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
