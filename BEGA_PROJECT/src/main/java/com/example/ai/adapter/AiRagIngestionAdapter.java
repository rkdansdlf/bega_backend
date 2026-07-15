package com.example.ai.adapter;

import com.example.ai.config.AiServiceSettings;
import com.example.ai.ingest.AiIngestRunRequest;
import com.example.ai.ingest.AiIngestRunStatusResponse;
import com.example.ai.ingest.AiIngestRunSubmission;
import com.example.ai.ingest.RagIngestionPort;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class AiRagIngestionAdapter
        implements RagIngestionPort {

    private final AiServiceSettings aiServiceSettings;
    private final RestTemplate restTemplate;

    public AiRagIngestionAdapter(
            AiServiceSettings aiServiceSettings,
            @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.aiServiceSettings = aiServiceSettings;
        this.restTemplate = restTemplate;
    }

    @Override
    public AiIngestRunSubmission submit(AiIngestRunRequest payload) {
        RequestTarget target = requireTarget("/ai/ingest/run");
        try {
            ResponseEntity<AiIngestRunSubmission> response = restTemplate.postForEntity(
                    target.url(),
                    new HttpEntity<>(payload, target.headers()),
                    AiIngestRunSubmission.class);
            AiIngestRunSubmission body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("AI ingestion submission returned an empty response");
            }
            log.info("AI ingestion run submitted target={} runId={} status={}",
                    aiServiceSettings.sanitizedServiceTarget(), body.runId(), body.status());
            return body;
        } catch (Exception exception) {
            log.error("AI ingestion submission failed target={} errorType={}",
                    aiServiceSettings.sanitizedServiceTarget(),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    @Override
    public AiIngestRunStatusResponse getStatus(UUID runId) {
        RequestTarget target = requireTarget("/ai/ingest/runs/" + runId);
        try {
            ResponseEntity<AiIngestRunStatusResponse> response = restTemplate.exchange(
                    target.url(),
                    HttpMethod.GET,
                    new HttpEntity<>(target.headers()),
                    AiIngestRunStatusResponse.class);
            AiIngestRunStatusResponse body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("AI ingestion status returned an empty response");
            }
            return body;
        } catch (Exception exception) {
            log.error("AI ingestion status lookup failed target={} runId={} errorType={}",
                    aiServiceSettings.sanitizedServiceTarget(),
                    runId,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private RequestTarget requireTarget(String path) {
        String url = aiServiceSettings.buildUrl(path);
        String internalToken = aiServiceSettings.getResolvedInternalToken();
        if (url.isBlank()) {
            throw new IllegalStateException("AI service URL is not configured");
        }
        if (internalToken.isBlank()) {
            throw new IllegalStateException("AI internal authentication is not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Internal-Api-Key", internalToken);
        return new RequestTarget(url, headers);
    }

    private record RequestTarget(String url, HttpHeaders headers) {}
}
