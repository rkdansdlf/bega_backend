package com.example.ai.controller;

import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.ai.service.AiProxyService.ProxyStreamResponse;
import lombok.RequiredArgsConstructor;
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
public class AiProxyController {

    private final AiProxyService aiProxyService;

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
        ProxyStreamResponse proxyResponse = aiProxyService.forwardJsonStream("/ai/coach/analyze", payload);
        return toStreamResponse(proxyResponse);
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

}
