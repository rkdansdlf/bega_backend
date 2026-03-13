package com.example.BegaDiary.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.BegaDiary.Entity.SeatViewClassificationResult;
import com.example.ai.config.AiServiceSettings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatViewClassificationService {

    private static final Duration AI_REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final AiServiceSettings aiServiceSettings;
    private final WebClient.Builder webClientBuilder;

    public SeatViewClassificationResult classify(MultipartFile file) {
        String aiServiceUrl = aiServiceSettings.getResolvedServiceUrl();
        String aiInternalToken = aiServiceSettings.getResolvedInternalToken();

        if (!StringUtils.hasText(aiServiceUrl) || !StringUtils.hasText(aiInternalToken)) {
            throw new IllegalStateException("Seat-view AI classifier is not configured.");
        }

        try {
            byte[] imageBytes = file.getBytes();
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "seat-view.jpg";
            WebClient client = webClientBuilder.baseUrl(Objects.requireNonNull(aiServiceUrl)).build();

            try {
                return classifyWithUri(client, imageBytes, fileName, "/ai/vision/seat-view-classify", aiInternalToken);
            } catch (WebClientResponseException ex) {
                if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                    log.warn("Primary seat-view AI endpoint not found, fallback to legacy path. cause={}", ex.getMessage());
                    return classifyWithUri(client, imageBytes, fileName, "/vision/seat-view-classify", aiInternalToken);
                }
                throw ex;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("시야 사진 파일을 읽는 중 오류가 발생했습니다.");
        }
    }

    private SeatViewClassificationResult classifyWithUri(
            WebClient client,
            byte[] imageBytes,
            String filename,
            String uri,
            String aiInternalToken) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        SeatViewClassificationResult result = client.post()
                .uri(uri)
                .header("X-Internal-Api-Key", aiInternalToken)
                .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA))
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(SeatViewClassificationResult.class)
                .block(AI_REQUEST_TIMEOUT);

        if (result == null) {
            throw new IllegalStateException("Seat-view AI classifier returned empty response.");
        }

        return result;
    }
}
