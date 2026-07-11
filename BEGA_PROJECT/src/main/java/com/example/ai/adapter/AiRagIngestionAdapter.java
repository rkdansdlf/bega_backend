package com.example.ai.adapter;

import com.example.ai.config.AiServiceSettings;
import com.example.cheerboard.service.port.RagIngestionPort;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class AiRagIngestionAdapter implements RagIngestionPort {

    private final AiServiceSettings aiServiceSettings;
    private final RestTemplate restTemplate;

    public AiRagIngestionAdapter(
            AiServiceSettings aiServiceSettings,
            @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.aiServiceSettings = aiServiceSettings;
        this.restTemplate = restTemplate;
    }

    @Override
    public void trigger() {
        String ingestUrl = aiServiceSettings.buildUrl("/ai/ingest/run");
        String aiInternalToken = aiServiceSettings.getResolvedInternalToken();

        if (ingestUrl.isBlank()) {
            log.error("AI service URL is not configured; cannot trigger RAG ingestion.");
            throw new IllegalStateException("AI service URL is not configured");
        }
        if (aiInternalToken.isBlank()) {
            log.error("AI internal token is not configured; cannot trigger RAG ingestion.");
            throw new IllegalStateException("AI internal authentication is not configured");
        }

        log.info("Triggering RAG ingestion job at: {}", ingestUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", aiInternalToken);
            Map<String, Object> payload = new HashMap<>();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(ingestUrl, request, String.class);

            log.info("Successfully triggered RAG ingestion job.");
        } catch (Exception exception) {
            log.error("Failed to trigger RAG ingestion: {}", exception.getMessage(), exception);
            throw exception;
        }
    }
}
