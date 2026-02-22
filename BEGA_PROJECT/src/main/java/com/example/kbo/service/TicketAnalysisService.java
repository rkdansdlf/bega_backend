package com.example.kbo.service;

import com.example.BegaDiary.Service.BegaGameService;
import com.example.kbo.dto.TicketInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAnalysisService {
    private static final Duration AI_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Value("${ai.service-url}")
    private String aiServiceUrl;

    @Value("${ai.internal-token:}")
    private String aiInternalToken;

    private final WebClient.Builder webClientBuilder;
    private final BegaGameService begaGameService;

    public TicketInfo analyzeTicket(MultipartFile file) {
        log.info("Analyzing ticket image: {}", file.getOriginalFilename());

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "ticket.jpg";
                }
            });

            WebClient client = webClientBuilder.baseUrl(Objects.requireNonNull(aiServiceUrl))
                    .build();

            var request = client.post()
                    .uri("/vision/ticket")
                    .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA))
                    .body(BodyInserters.fromMultipartData(builder.build()));

            if (StringUtils.hasText(aiInternalToken)) {
                request = request.header("X-Internal-Api-Key", aiInternalToken);
            } else {
                log.warn("ai.internal-token is not configured; ticket analysis request may be rejected.");
            }

            TicketInfo info = request
                    .retrieve()
                    .bodyToMono(TicketInfo.class)
                    .block(AI_REQUEST_TIMEOUT); // Blocking for now to keep it simple in Spring MVC

            if (info == null) {
                throw new RuntimeException("AI Service returned empty response");
            }

            if (info.getDate() != null && info.getHomeTeam() != null && info.getAwayTeam() != null) {
                Long gameId = begaGameService.findGameIdByDateAndTeams(info.getDate(), info.getHomeTeam(),
                        info.getAwayTeam(), info.getStadium(), info.getTime());
                info.setGameId(gameId);
                log.info("Matched game ID: {}", gameId);
            }

            return Objects.requireNonNull(info);

        } catch (IOException e) {
            log.error("Failed to read ticket image", e);
            throw new RuntimeException("Failed to process ticket image", e);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
                log.error("AI Service call timed out after {} seconds", AI_REQUEST_TIMEOUT.toSeconds(), e);
                throw new RuntimeException("AI Service analysis timed out", e);
            }
            log.error("AI Service call failed", e);
            throw new RuntimeException("AI Service analysis failed", e);
        } catch (Exception e) {
            log.error("AI Service call failed", e);
            throw new RuntimeException("AI Service analysis failed", e);
        }
    }
}
