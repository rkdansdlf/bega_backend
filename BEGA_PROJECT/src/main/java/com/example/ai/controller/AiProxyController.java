package com.example.ai.controller;

import com.example.ai.config.AiProxyRequestLimits;
import com.example.ai.dto.AiStreamHttpErrorResponse;
import com.example.ai.exception.AiProxyException;
import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.ai.service.AiProxyService.ProxyStreamResponse;
import com.example.ai.service.AiProxyStreamConcurrencyLimiter;
import com.example.ai.service.AiProxyStreamConcurrencyLimiter.Permit;
import com.example.ai.service.CoachAutoBriefMonitoringService;
import com.example.common.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiProxyController {

    private final AiProxyService aiProxyService;
    private final CoachAutoBriefMonitoringService coachAutoBriefMonitoringService;
    private final AiProxyRequestLimits requestLimits;
    private final AiProxyStreamConcurrencyLimiter streamConcurrencyLimiter;

    @PostMapping("/chat/completion")
    @RateLimit(limit = 60, window = 60, key = "ai:chat", failClosed = true)
    public ResponseEntity<byte[]> chatCompletion(@RequestBody String payload) {
        requestLimits.validateChatJson(payload);
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/chat/completion", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/chat/stream")
    @RateLimit(limit = 60, window = 60, key = "ai:chat", failClosed = true)
    public ResponseEntity<StreamingResponseBody> chatStream(
            @RequestBody String payload,
            @RequestHeader(value = AiProxyService.AI_EVENT_VERSION_HEADER, required = false) String eventVersion) {
        Permit streamPermit = null;
        try {
            requestLimits.validateChatJson(payload);
            streamPermit = streamConcurrencyLimiter.acquire("chat_stream");
            ProxyStreamResponse proxyResponse = forwardJsonStream("/ai/chat/stream", payload, eventVersion);
            return toStreamResponse(proxyResponse, streamPermit);
        } catch (AiProxyException exception) {
            closeStreamPermit(streamPermit);
            return toLocalStreamErrorResponse(exception);
        } catch (RuntimeException exception) {
            closeStreamPermit(streamPermit);
            throw exception;
        }
    }

    @PostMapping("/chat/voice")
    @RateLimit(limit = 20, window = 60, key = "ai:chat_voice", failClosed = true)
    public ResponseEntity<byte[]> chatVoice(@RequestPart("file") MultipartFile file) {
        requestLimits.validateVoiceUpload(file);
        ProxyByteResponse proxyResponse = aiProxyService.forwardMultipart("/ai/chat/voice", file);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/coach/analyze")
    @RateLimit(limit = 25, window = 60, key = "ai:coach", failClosed = true)
    public ResponseEntity<StreamingResponseBody> coachAnalyze(
            @RequestBody String payload,
            @RequestHeader(value = AiProxyService.AI_EVENT_VERSION_HEADER, required = false) String eventVersion) {
        String requestMode = coachAutoBriefMonitoringService.extractRequestMode(payload);
        String analysisType = coachAutoBriefMonitoringService.extractAnalysisType(payload);
        long startNanos = System.nanoTime();
        Permit streamPermit = null;

        try {
            requestLimits.validateCoachJson(payload);
            streamPermit = streamConcurrencyLimiter.acquire("coach_analyze");
            log.info("Coach analyze proxy start request_mode={} analysis_type={}", requestMode, analysisType);
            ProxyStreamResponse proxyResponse = forwardJsonStream("/ai/coach/analyze", payload, eventVersion);
            log.info(
                    "Coach analyze upstream stream established request_mode={} analysis_type={} status={} header_wait_ms={}",
                    requestMode,
                    analysisType,
                    proxyResponse.status().value(),
                    elapsedMillis(startNanos));
            return toCoachAnalyzeStreamResponse(proxyResponse, requestMode, analysisType, startNanos, streamPermit);
        } catch (AiProxyException exception) {
            closeStreamPermit(streamPermit);
            coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                    requestMode,
                    analysisType,
                    exception.getStatus().value(),
                    System.nanoTime() - startNanos);
            log.warn(
                    "Coach analyze proxy setup failed request_mode={} analysis_type={} status={} elapsed_ms={} code={}",
                    requestMode,
                    analysisType,
                    exception.getStatus().value(),
                    elapsedMillis(startNanos),
                    exception.getCode());
            return toLocalStreamErrorResponse(exception);
        } catch (RuntimeException exception) {
            closeStreamPermit(streamPermit);
            int statusCode = coachAutoBriefMonitoringService.resolveStatusCode(exception);
            coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                    requestMode,
                    analysisType,
                    statusCode,
                    System.nanoTime() - startNanos);
            log.warn(
                    "Coach analyze proxy failed before stream request_mode={} analysis_type={} status={} elapsed_ms={} error={}",
                    requestMode,
                    analysisType,
                    statusCode,
                    elapsedMillis(startNanos),
                    exception.toString());
            throw exception;
        }
    }

    @GetMapping("/coach/auto-brief/ops/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> coachAutoBriefOpsHealth(HttpServletRequest request) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet(withQuery("/ai/coach/auto-brief/ops/health", request));
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/presets")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionPresets() {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/presets");
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/eval-cases")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionEvalCases() {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/eval-cases");
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/artifacts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionArtifacts() {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/artifacts");
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/artifacts/{artifactId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionArtifactDetail(@PathVariable String artifactId) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/artifacts/" + artifactId);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionDraft(@RequestBody String payload) {
        requestLimits.validateAdminJson(payload);
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/release-decision/draft", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionEvaluate(@RequestBody String payload) {
        requestLimits.validateAdminJson(payload);
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/release-decision/evaluate", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/save")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionSave(@RequestBody String payload) {
        requestLimits.validateAdminJson(payload);
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/release-decision/save", payload);
        return toByteResponse(proxyResponse);
    }

    private ResponseEntity<byte[]> toByteResponse(ProxyByteResponse proxyResponse) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());
        return ResponseEntity.status(proxyResponse.status())
                .headers(headers)
                .body(proxyResponse.body());
    }

    private ResponseEntity<StreamingResponseBody> toLocalStreamErrorResponse(AiProxyException exception) {
        AiStreamHttpErrorResponse error = new AiStreamHttpErrorResponse(
                exception.getCode(),
                exception.getStatus().is5xxServerError()
                        ? safeProxyMessage(exception.getCode())
                        : exception.getMessage(),
                null,
                isRetryableProxySetupFailure(exception),
                null,
                List.of());
        byte[] body = aiProxyService.serializeStreamError(error);
        return ResponseEntity.status(exception.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(outputStream -> outputStream.write(body));
    }

    private String safeProxyMessage(String code) {
        return switch (code) {
            case "AI_UPSTREAM_TIMEOUT" -> "AI 응답 시간이 초과되었습니다.";
            case "AI_UPSTREAM_CONNECTION_FAILED" -> "AI 서비스 연결에 실패했습니다.";
            case "AI_PROXY_STREAMS_BUSY" -> "AI 스트리밍 요청이 많습니다. 잠시 후 다시 시도해주세요.";
            case "AI_SERVICE_URL_NOT_CONFIGURED" -> "AI 서비스 주소가 설정되지 않았습니다.";
            case "AI_SERVICE_URL_INVALID" -> "AI 서비스 주소 설정이 올바르지 않습니다.";
            case "AI_INTERNAL_AUTH_MISCONFIGURED" -> "AI 내부 인증 설정이 누락되었습니다.";
            default -> "AI 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.";
        };
    }

    private boolean isRetryableProxySetupFailure(AiProxyException exception) {
        return exception.getStatus().value() == 429
                || switch (exception.getCode()) {
                    case "AI_UPSTREAM_TIMEOUT",
                            "AI_UPSTREAM_CONNECTION_FAILED",
                            "AI_UPSTREAM_EMPTY_RESPONSE",
                            "AI_PROXY_STREAMS_BUSY" -> true;
                    default -> false;
                };
    }

    private void closeStreamPermit(Permit streamPermit) {
        if (streamPermit != null) {
            streamPermit.close();
        }
    }

    private ProxyStreamResponse forwardJsonStream(String uri, String payload, String eventVersion) {
        if (eventVersion == null || eventVersion.isBlank()) {
            return aiProxyService.forwardJsonStream(uri, payload);
        }
        return aiProxyService.forwardJsonStream(uri, payload, eventVersion);
    }

    private ResponseEntity<StreamingResponseBody> toStreamResponse(ProxyStreamResponse proxyResponse, Permit streamPermit) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());

        StreamingResponseBody responseBody;
        if (!proxyResponse.status().is2xxSuccessful()) {
            byte[] errorBody = proxyResponse.errorBody() != null ? proxyResponse.errorBody() : new byte[0];
            responseBody = outputStream -> {
                try {
                    outputStream.write(errorBody);
                } finally {
                    streamPermit.close();
                }
            };
        } else {
            responseBody = outputStream -> {
                try {
                    aiProxyService.writeStream(proxyResponse.bodyFlux(), outputStream);
                } finally {
                    streamPermit.close();
                }
            };
        }
        return ResponseEntity.status(proxyResponse.status())
                .headers(headers)
                .body(responseBody);
    }

    private ResponseEntity<StreamingResponseBody> toCoachAnalyzeStreamResponse(
            ProxyStreamResponse proxyResponse,
            String requestMode,
            String analysisType,
            long startNanos,
            Permit streamPermit) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());

        StreamingResponseBody responseBody;
        if (!proxyResponse.status().is2xxSuccessful()) {
            byte[] errorBody = proxyResponse.errorBody() != null ? proxyResponse.errorBody() : new byte[0];
            responseBody = outputStream -> {
                try {
                    outputStream.write(errorBody);
                } finally {
                    try {
                        coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                                requestMode,
                                analysisType,
                                proxyResponse.status().value(),
                                System.nanoTime() - startNanos);
                        log.info(
                                "Coach analyze proxy error response completed request_mode={} analysis_type={} status={} elapsed_ms={}",
                                requestMode,
                                analysisType,
                                proxyResponse.status().value(),
                                elapsedMillis(startNanos));
                    } finally {
                        streamPermit.close();
                    }
                }
            };
        } else {
            responseBody = outputStream -> {
                try {
                    aiProxyService.writeStream(proxyResponse.bodyFlux(), outputStream);
                } catch (IOException | RuntimeException exception) {
                    coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                            requestMode,
                            analysisType,
                            proxyResponse.status().value(),
                            System.nanoTime() - startNanos);
                    log.warn(
                            "Coach analyze stream interrupted request_mode={} analysis_type={} status={} elapsed_ms={} error={}",
                            requestMode,
                            analysisType,
                            proxyResponse.status().value(),
                            elapsedMillis(startNanos),
                            exception.toString());
                    throw exception;
                } finally {
                    streamPermit.close();
                }
                coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                        requestMode,
                        analysisType,
                        proxyResponse.status().value(),
                        System.nanoTime() - startNanos);
                log.info(
                        "Coach analyze stream completed request_mode={} analysis_type={} status={} elapsed_ms={}",
                        requestMode,
                        analysisType,
                        proxyResponse.status().value(),
                        elapsedMillis(startNanos));
            };
        }

        return ResponseEntity.status(proxyResponse.status())
                .headers(headers)
                .body(responseBody);
    }

    private String withQuery(String path, HttpServletRequest request) {
        String queryString = request != null ? request.getQueryString() : null;
        if (queryString == null || queryString.isBlank()) {
            return path;
        }
        return path + "?" + queryString;
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

}
