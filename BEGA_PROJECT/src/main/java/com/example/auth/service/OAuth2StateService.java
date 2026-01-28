package com.example.auth.service;

import com.example.auth.dto.OAuth2StateData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2StateService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "oauth2:state:";
    private static final Duration TTL = Duration.ofMinutes(5);

    public String saveState(OAuth2StateData data) {
        String stateId = UUID.randomUUID().toString();
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(PREFIX + stateId, json, TTL);
            log.info("OAuth2 state saved: stateId={}", stateId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OAuth2 state data", e);
        }
        return stateId;
    }

    public OAuth2StateData consumeState(String stateId) {
        String key = PREFIX + stateId;
        String json = redisTemplate.opsForValue().getAndDelete(key);
        if (json == null) {
            log.warn("OAuth2 state not found or already consumed: stateId={}", stateId);
            return null;
        }
        try {
            log.info("OAuth2 state consumed: stateId={}", stateId);
            return objectMapper.readValue(json, OAuth2StateData.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize OAuth2 state data", e);
        }
    }
}
