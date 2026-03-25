package com.example.cheerboard.service;

import com.example.ai.config.AiServiceSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiIntegrationService {

    private final AiServiceSettings aiServiceSettings;
    private final RestTemplate restTemplate;

    /**
     * AI RAG 파이프라인 인덱싱 작업을 트리거합니다.
     * JobRunr에 의해 백그라운드에서 실행됩니다.
     */
    @Job(name = "Trigger RAG Pipeline Ingestion")
    public void triggerRagIngestion() {
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

            // 기본 설정으로 전체 인덱싱 수행 (필요 시 파라미터화 가능)
            Map<String, Object> payload = new HashMap<>();
            // payload.put("season_year", 2024); // 예시: 특정 연도만

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // AI 서비스는 비동기(Accepted) 응답을 반환할 것으로 예상됨
            restTemplate.postForEntity(ingestUrl, request, String.class);

            log.info("Successfully triggered RAG ingestion job.");
        } catch (Exception e) {
            log.error("Failed to trigger RAG ingestion: {}", e.getMessage(), e);
            throw e; // JobRunr가 재시도할 수 있도록 에러 전파
        }
    }
}
