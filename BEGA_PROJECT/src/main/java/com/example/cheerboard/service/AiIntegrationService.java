package com.example.cheerboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * AI RAG 파이프라인 인덱싱 작업을 트리거합니다.
     * JobRunr에 의해 백그라운드에서 실행됩니다.
     */
    @Job(name = "Trigger RAG Pipeline Ingestion")
    public void triggerRagIngestion() {
        String ingestUrl = aiServiceUrl + "/ingest/run";
        log.info("Triggering RAG ingestion job at: {}", ingestUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

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
