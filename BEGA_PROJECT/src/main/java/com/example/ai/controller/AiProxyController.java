package com.example.ai.controller;

import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.ai.service.AiProxyService.ProxyStreamResponse;
import com.example.ai.service.CoachAutoBriefMonitoringService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
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

    @PostMapping("/chat/completion")
    public ResponseEntity<byte[]> chatCompletion(@RequestBody String payload) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/chat/completion", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/chat/stream")
    public ResponseEntity<StreamingResponseBody> chatStream(@RequestBody String payload) {
        ProxyStreamResponse proxyResponse = aiProxyService.forwardJsonStream("/ai/chat/stream", payload);
        return toStreamResponse(proxyResponse);
    }

    @PostMapping("/chat/voice")
    public ResponseEntity<byte[]> chatVoice(@RequestPart("file") MultipartFile file) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardMultipart("/ai/chat/voice", file);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/coach/analyze")
    public ResponseEntity<StreamingResponseBody> coachAnalyze(@RequestBody String payload) {
        String requestMode = coachAutoBriefMonitoringService.extractRequestMode(payload);
        long startNanos = System.nanoTime();

        try {
            log.info("Coach analyze proxy start request_mode={}", requestMode);
            ProxyStreamResponse proxyResponse = aiProxyService.forwardJsonStream("/ai/coach/analyze", payload);
            log.info(
                    "Coach analyze upstream stream established request_mode={} status={} header_wait_ms={}",
                    requestMode,
                    proxyResponse.status().value(),
                    elapsedMillis(startNanos));
            return toCoachAnalyzeStreamResponse(proxyResponse, requestMode, startNanos);
        } catch (RuntimeException exception) {
            int statusCode = coachAutoBriefMonitoringService.resolveStatusCode(exception);
            coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                    requestMode,
                    statusCode,
                    System.nanoTime() - startNanos);
            log.warn(
                    "Coach analyze proxy failed before stream request_mode={} status={} elapsed_ms={} error={}",
                    requestMode,
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
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/release-decision/draft", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionEvaluate(@RequestBody String payload) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/release-decision/evaluate", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/save")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> releaseDecisionSave(@RequestBody String payload) {
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

    private ResponseEntity<StreamingResponseBody> toStreamResponse(ProxyStreamResponse proxyResponse) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());

        StreamingResponseBody responseBody;
        if (!proxyResponse.status().is2xxSuccessful()) {
            byte[] errorBody = proxyResponse.errorBody() != null ? proxyResponse.errorBody() : new byte[0];
            responseBody = outputStream -> outputStream.write(errorBody);
        } else {
            responseBody = outputStream -> aiProxyService.writeStream(proxyResponse.bodyFlux(), outputStream);
        }
        return ResponseEntity.status(proxyResponse.status())
                .headers(headers)
                .body(responseBody);
    }

    private ResponseEntity<StreamingResponseBody> toCoachAnalyzeStreamResponse(
            ProxyStreamResponse proxyResponse,
            String requestMode,
            long startNanos) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());

        StreamingResponseBody responseBody;
        if (!proxyResponse.status().is2xxSuccessful()) {
            byte[] errorBody = proxyResponse.errorBody() != null ? proxyResponse.errorBody() : new byte[0];
            responseBody = outputStream -> {
                try {
                    outputStream.write(errorBody);
                } finally {
                    coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                            requestMode,
                            proxyResponse.status().value(),
                            System.nanoTime() - startNanos);
                    log.info(
                            "Coach analyze proxy error response completed request_mode={} status={} elapsed_ms={}",
                            requestMode,
                            proxyResponse.status().value(),
                            elapsedMillis(startNanos));
                }
            };
        } else {
            responseBody = outputStream -> {
                try {
                    aiProxyService.writeStream(proxyResponse.bodyFlux(), outputStream);
                } catch (IOException | RuntimeException exception) {
                    coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                            requestMode,
                            proxyResponse.status().value(),
                            System.nanoTime() - startNanos);
                    log.warn(
                            "Coach analyze stream interrupted request_mode={} status={} elapsed_ms={} error={}",
                            requestMode,
                            proxyResponse.status().value(),
                            elapsedMillis(startNanos),
                            exception.toString());
                    throw exception;
                }
                coachAutoBriefMonitoringService.recordCoachAnalyzeDuration(
                        requestMode,
                        proxyResponse.status().value(),
                        System.nanoTime() - startNanos);
                log.info(
                        "Coach analyze stream completed request_mode={} status={} elapsed_ms={}",
                        requestMode,
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
