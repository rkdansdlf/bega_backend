package com.example.ai.adapter;

import com.example.ai.config.AiServiceSettings;
import com.example.common.service.port.ContentModerationDecision;
import com.example.common.service.port.ContentModerationPort;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class AiContentModerationAdapter implements ContentModerationPort {

    private final AiServiceSettings aiServiceSettings;
    private final RestTemplate restTemplate;

    public AiContentModerationAdapter(
            AiServiceSettings aiServiceSettings,
            @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.aiServiceSettings = aiServiceSettings;
        this.restTemplate = restTemplate;
    }

    @Override
    public Optional<ContentModerationDecision> moderate(String content) {
        String url = aiServiceSettings.buildUrl("/moderation/safety-check");
        if (!StringUtils.hasText(url)) {
            log.warn("AI moderation service URL is not configured; using rule fallback.");
            return Optional.empty();
        }

        String aiInternalToken = aiServiceSettings.getResolvedInternalToken();
        if (!StringUtils.hasText(aiInternalToken)) {
            log.warn("AI moderation internal token is not configured; using rule fallback.");
            return Optional.empty();
        }

        try {
            Map<String, String> request = Map.of("content", content);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", aiInternalToken);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(request, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
            if (response == null) {
                log.warn("AI moderation response is null; using rule fallback.");
                return Optional.empty();
            }

            return Optional.of(new ContentModerationDecision(
                    asString(response.get("category"), "SAFE"),
                    asString(response.get("reason"), ""),
                    asString(response.get("action"), "ALLOW"),
                    asString(response.get("decisionSource"), "MODEL"),
                    asString(response.get("riskLevel"), "LOW")));
        } catch (Exception exception) {
            log.error("AI moderation request failed. Using rule fallback.", exception);
            return Optional.empty();
        }
    }

    private String asString(Object value, String defaultValue) {
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        return defaultValue;
    }
}
