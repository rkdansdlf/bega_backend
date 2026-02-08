package com.example.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIModerationService {

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public ModerationResult checkContent(String content) {
        if (content == null || content.isBlank()) {
            return ModerationResult.allow();
        }

        try {
            String url = aiServiceUrl + "/moderation/safety-check";
            Map<String, String> request = Map.of("content", content);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response == null) {
                return ModerationResult.allow();
            }

            String category = (String) response.getOrDefault("category", "SAFE");
            String reason = (String) response.getOrDefault("reason", "");
            String action = (String) response.getOrDefault("action", "ALLOW");

            return new ModerationResult(category, reason, "ALLOW".equalsIgnoreCase(action));
        } catch (Exception e) {
            log.error("AI Moderation failed: {}", e.getMessage());
            return ModerationResult.allow(); // Fail-open
        }
    }

    public record ModerationResult(String category, String reason, boolean isAllowed) {
        public static ModerationResult allow() {
            return new ModerationResult("SAFE", "", true);
        }
    }
}
