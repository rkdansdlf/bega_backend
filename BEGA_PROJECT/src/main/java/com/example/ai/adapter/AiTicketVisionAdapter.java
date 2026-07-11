package com.example.ai.adapter;

import com.example.ai.config.AiServiceSettings;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.exception.TicketAnalysisException;
import com.example.kbo.service.port.TicketVisionPort;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiTicketVisionAdapter implements TicketVisionPort {

    private static final Duration AI_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final AiServiceSettings aiServiceSettings;
    private final WebClient.Builder webClientBuilder;

    @Override
    public TicketInfo analyze(MultipartFile file) {
        String aiServiceUrl = aiServiceSettings.getResolvedServiceUrl();
        if (!StringUtils.hasText(aiServiceUrl)) {
            log.error("AI service URL is not configured for ticket analysis.");
            throw new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 주소가 설정되지 않았습니다.");
        }

        String aiInternalToken = aiServiceSettings.getResolvedInternalToken();
        if (!StringUtils.hasText(aiInternalToken)) {
            log.error("ai.internal-token is not configured; cannot call ticket analysis endpoint.");
            throw new TicketAnalysisException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "티켓 분석 서비스 인증 설정이 누락되었습니다. 관리자에게 문의해주세요.");
        }

        try {
            byte[] imageBytes = file.getBytes();
            String fileName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "ticket.jpg";
            WebClient client = webClientBuilder
                    .baseUrl(Objects.requireNonNull(aiServiceUrl))
                    .build();

            try {
                return analyzeWithUri(client, imageBytes, fileName, "/ai/vision/ticket", aiInternalToken);
            } catch (WebClientResponseException exception) {
                if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                    log.warn("Primary AI ticket endpoint not found, fallback to legacy path. cause={}",
                            exception.getMessage());
                    return analyzeWithUri(client, imageBytes, fileName, "/vision/ticket", aiInternalToken);
                }
                throw mapUpstreamException(exception);
            }
        } catch (IOException exception) {
            log.error("Failed to read ticket image", exception);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_REQUEST,
                    "티켓 이미지를 읽는 중 오류가 발생했습니다.");
        } catch (WebClientResponseException exception) {
            log.error("AI Service returned error response. status={}",
                    exception.getStatusCode().value(), exception);
            throw mapUpstreamException(exception);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("Timeout on blocking read")) {
                log.error("AI Service call timed out after {} seconds",
                        AI_REQUEST_TIMEOUT.toSeconds(), exception);
                throw new TicketAnalysisException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "티켓 분석 요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
            }
            log.error("AI Service call failed", exception);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 서비스를 호출하지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (TicketAnalysisException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("AI Service call failed", exception);
            throw new TicketAnalysisException(
                    HttpStatus.BAD_GATEWAY,
                    "티켓 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private TicketInfo analyzeWithUri(
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

        TicketInfo info = client.post()
                .uri(uri)
                .header("X-Internal-Api-Key", aiInternalToken)
                .contentType(Objects.requireNonNull(MediaType.MULTIPART_FORM_DATA))
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(TicketInfo.class)
                .block(AI_REQUEST_TIMEOUT);

        if (info == null) {
            throw new RuntimeException("AI Service returned empty response");
        }
        return info;
    }

    private TicketAnalysisException mapUpstreamException(WebClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        if (statusCode == HttpStatus.UNAUTHORIZED.value()
                || statusCode == HttpStatus.FORBIDDEN.value()) {
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
