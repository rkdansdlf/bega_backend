package com.example.kbo.service;

import com.example.BegaDiary.Service.BegaGameService;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.exception.TicketAnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

        if (!StringUtils.hasText(aiServiceUrl)) {
            throw new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 주소가 설정되지 않았습니다.");
        }
        if (!StringUtils.hasText(aiInternalToken)) {
            log.error("ai.internal-token is not configured; cannot call ticket analysis endpoint.");
            throw new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 인증 설정이 누락되었습니다. 관리자에게 문의해주세요.");
        }

        try {
            byte[] imageBytes = file.getBytes();
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "ticket.jpg";
            WebClient client = webClientBuilder.baseUrl(Objects.requireNonNull(aiServiceUrl))
                    .build();

            try {
                return analyzeTicketWithUri(client, imageBytes, fileName, "/ai/vision/ticket");
            } catch (WebClientResponseException ex) {
                if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                    log.warn("Primary AI ticket endpoint not found, fallback to legacy path. cause={}", ex.getMessage());
                    return analyzeTicketWithUri(client, imageBytes, fileName, "/vision/ticket");
                }
                throw mapUpstreamException(ex);
            }

        } catch (IOException e) {
            log.error("Failed to read ticket image", e);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "티켓 이미지를 읽는 중 오류가 발생했습니다.");
        } catch (WebClientResponseException e) {
            log.error("AI Service returned error response. status={}", e.getStatusCode().value(), e);
            throw mapUpstreamException(e);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Timeout on blocking read")) {
                log.error("AI Service call timed out after {} seconds", AI_REQUEST_TIMEOUT.toSeconds(), e);
                throw new TicketAnalysisException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "티켓 분석 요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
            }
            log.error("AI Service call failed", e);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 서비스를 호출하지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (TicketAnalysisException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI Service call failed", e);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private TicketInfo analyzeTicketWithUri(WebClient client, byte[] imageBytes, String filename, String uri) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        var request = client.post()
                .uri(uri)
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
                .block(AI_REQUEST_TIMEOUT);

        if (info == null) {
            throw new RuntimeException("AI Service returned empty response");
        }

        return enrichTicketInfoWithGameId(info);
    }

    private TicketInfo enrichTicketInfoWithGameId(TicketInfo info) {
        if (info == null) {
            return null;
        }

        if (info.getDate() != null && info.getHomeTeam() != null && info.getAwayTeam() != null) {
            Long gameId = begaGameService.findGameIdByDateAndTeams(
                    info.getDate(),
                    info.getHomeTeam(),
                    info.getAwayTeam(),
                    info.getStadium(),
                    info.getTime());
            info.setGameId(gameId);
            log.info("Matched game ID: {}", gameId);
        }

        return Objects.requireNonNull(info);
    }

    private TicketAnalysisException mapUpstreamException(WebClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();

        if (statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
            return new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 인증에 실패했습니다. 서버 설정을 확인해주세요.");
        }

        if (statusCode == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일 크기가 너무 큽니다. (최대 5MB)");
        }

        if (statusCode == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "지원되지 않는 이미지 형식입니다. JPG, PNG, WEBP 파일만 가능합니다.");
        }

        if (statusCode >= 400 && statusCode < 500) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "티켓 분석 요청이 올바르지 않습니다. 다른 파일로 다시 시도해주세요.");
        }

        if (statusCode >= 500) {
            return new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 AI 서버에서 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }

        return new TicketAnalysisException(
                HttpStatus.BAD_GATEWAY,
                "티켓 분석 서비스 호출에 실패했습니다.");
    }
}
