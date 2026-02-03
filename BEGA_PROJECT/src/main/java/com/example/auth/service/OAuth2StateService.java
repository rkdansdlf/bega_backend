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
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    private static final String PREFIX = "oauth2:state:";
    private static final Duration TTL = Duration.ofMinutes(5);

    /**
     * OAuth2 인증 상태 저장 (userId만 Redis에 저장)
     * @param userId 사용자 ID
     * @return 생성된 stateId
     */
    public String saveState(Long userId) {
        String stateId = UUID.randomUUID().toString();
        try {
            OAuth2StateStorageData storageData = OAuth2StateStorageData.of(userId);
            String json = objectMapper.writeValueAsString(storageData);
            redisTemplate.opsForValue().set(PREFIX + stateId, json, TTL);
            log.info("OAuth2 state saved: stateId={}, userId={}", stateId, userId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OAuth2 state data", e);
        }
        return stateId;
    }

    /**
     * @deprecated Use saveState(Long userId) instead
     */
    @Deprecated
    public String saveState(OAuth2StateData data) {
        // 하위 호환성: OAuth2StateData에서 userId를 추출하지 못하므로 레거시 지원 불가
        throw new UnsupportedOperationException(
            "saveState(OAuth2StateData) is deprecated. Use saveState(Long userId) instead."
        );
    }

    /**
     * OAuth2 인증 상태 소비 (일회성, DB에서 사용자 정보 조회)
     * @param stateId state ID
     * @return 사용자 정보 (없으면 null)
     */
    public OAuth2StateData consumeState(String stateId) {
        String key = PREFIX + stateId;
        String json = redisTemplate.opsForValue().getAndDelete(key);
        if (json == null) {
            log.warn("OAuth2 state not found or already consumed: stateId={}", stateId);
            return null;
        }

        try {
            OAuth2StateStorageData storageData = objectMapper.readValue(json, OAuth2StateStorageData.class);

            // 만료 확인
            if (storageData.isExpired()) {
                log.warn("OAuth2 state expired: stateId={}, createdAt={}", stateId, storageData.createdAt());
                return null;
            }

            // userId null 체크
            Long userId = storageData.userId();
            if (userId == null) {
                log.warn("OAuth2 state has null userId: stateId={}", stateId);
                return null;
            }

            // DB에서 사용자 정보 조회
            Optional<UserEntity> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found for OAuth2 state: stateId={}, userId={}", stateId, storageData.userId());
                return null;
            }

            UserEntity user = userOpt.get();
            String favoriteTeamId = user.getFavoriteTeamId();
            if (favoriteTeamId == null || favoriteTeamId.isEmpty()) {
                favoriteTeamId = "없음";
            }

            log.info("OAuth2 state consumed: stateId={}, userId={}", stateId, user.getId());

            // 응답용 DTO 생성 (민감 정보는 DB에서 조회)
            return new OAuth2StateData(
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getProfileImageUrl(),
                favoriteTeamId,
                user.getHandle()
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize OAuth2 state data: stateId={}", stateId, e);
            return null;
        }
    }

    /**
     * 저장된 userId만 조회 (state 삭제하지 않음)
     * @param stateId state ID
     * @return userId (없으면 null)
     */
    public Long peekUserId(String stateId) {
        String key = PREFIX + stateId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            OAuth2StateStorageData storageData = objectMapper.readValue(json, OAuth2StateStorageData.class);
            return storageData.userId();
        } catch (JsonProcessingException e) {
            log.error("Failed to peek OAuth2 state: stateId={}", stateId, e);
            return null;
        }
    }
}
