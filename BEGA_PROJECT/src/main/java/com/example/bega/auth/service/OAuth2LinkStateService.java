package com.example.bega.auth.service;

import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 계정 연동 상태 관리 서비스
 * Redis를 사용하여 state 파라미터와 연동 정보를 매핑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2LinkStateService {

    private static final String PREFIX = "oauth2:link:";
    private static final long TTL_MINUTES = 5;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 연동 상태 저장 (원본 OAuth2 state를 key로 사용)
     *
     * @param state OAuth2 원본 state 값
     * @param data  연동 상태 데이터
     */
    public void saveLinkByState(String state, OAuth2LinkStateData data) {
        try {
            String key = PREFIX + state;
            String json = objectMapper.writeValueAsString(data);

            redisTemplate.opsForValue().set(key, json, TTL_MINUTES, TimeUnit.MINUTES);

            log.info("Saved OAuth2 link state by original state: state={}, userId={}, mode={}",
                state, data.userId(), data.mode());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OAuth2LinkStateData", e);
            throw new RuntimeException("Failed to save OAuth2 link state", e);
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
            String key = PREFIX + state;
            String json = redisTemplate.opsForValue().getAndDelete(key);

            if (json == null) {
                // 일반 로그인 (연동 모드 아님) - 정상 케이스
                log.debug("No link state found for state: {} (normal login)", state);
                return null;
            }

            OAuth2LinkStateData data = objectMapper.readValue(json, OAuth2LinkStateData.class);

            // 만료 확인
            if (data.isExpired()) {
                log.warn("OAuth2 link state expired: state={}, createdAt={}", state, data.createdAt());
                return null;
            }

            log.info("Consumed OAuth2 link state: state={}, userId={}, mode={}",
                state, data.userId(), data.mode());

            return data;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize OAuth2LinkStateData", e);
            return null;
        }
    }

    /**
     * @deprecated Use saveLinkByState instead
     */
    @Deprecated
    public String saveLink(OAuth2LinkStateData data) {
        String linkStateId = UUID.randomUUID().toString();
        saveLinkByState(linkStateId, data);
        return linkStateId;
    }

    /**
     * @deprecated Use consumeLinkByState instead
     */
    @Deprecated
    public OAuth2LinkStateData consumeLink(String linkStateId) {
        return consumeLinkByState(linkStateId);
    }
}
