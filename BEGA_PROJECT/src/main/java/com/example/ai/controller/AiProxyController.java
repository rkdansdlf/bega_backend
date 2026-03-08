package com.example.ai.controller;

import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseEntity<byte[]> chatStream(@RequestBody String payload) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/chat/stream", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/chat/voice")
    public ResponseEntity<byte[]> chatVoice(@RequestPart("file") MultipartFile file) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardMultipart("/ai/chat/voice", file);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/coach/analyze")
    public ResponseEntity<byte[]> coachAnalyze(@RequestBody String payload) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/coach/analyze", payload);
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/presets")
    public ResponseEntity<byte[]> releaseDecisionPresets() {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/presets");
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/eval-cases")
    public ResponseEntity<byte[]> releaseDecisionEvalCases() {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/eval-cases");
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/artifacts")
    public ResponseEntity<byte[]> releaseDecisionArtifacts() {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/artifacts");
        return toByteResponse(proxyResponse);
    }

    @GetMapping("/release-decision/artifacts/{artifactId}")
    public ResponseEntity<byte[]> releaseDecisionArtifactDetail(@PathVariable String artifactId) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardGet("/ai/release-decision/artifacts/" + artifactId);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/draft")
    public ResponseEntity<byte[]> releaseDecisionDraft(@RequestBody String payload) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/release-decision/draft", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/evaluate")
    public ResponseEntity<byte[]> releaseDecisionEvaluate(@RequestBody String payload) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardJson("/ai/release-decision/evaluate", payload);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/release-decision/save")
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

}
