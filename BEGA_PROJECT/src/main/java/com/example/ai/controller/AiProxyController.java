package com.example.ai.controller;

import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.ai.service.AiProxyService.ProxyStreamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
        if (!proxyResponse.status().is2xxSuccessful()) {
            return toProxyErrorStreamResponse(proxyResponse);
        }
        StreamingResponseBody responseBody = outputStream -> aiProxyService.writeStream(
                proxyResponse.bodyFlux(),
                outputStream);
        return toStreamResponse(proxyResponse, responseBody);
    }

    @PostMapping("/chat/voice")
    public ResponseEntity<byte[]> chatVoice(@RequestPart("file") MultipartFile file) {
        ProxyByteResponse proxyResponse = aiProxyService.forwardMultipart("/ai/chat/voice", file);
        return toByteResponse(proxyResponse);
    }

    @PostMapping("/coach/analyze")
    public ResponseEntity<StreamingResponseBody> coachAnalyze(@RequestBody String payload) {
        ProxyStreamResponse proxyResponse = aiProxyService.forwardJsonStream("/ai/coach/analyze", payload);
        if (!proxyResponse.status().is2xxSuccessful()) {
            return toProxyErrorStreamResponse(proxyResponse);
        }
        StreamingResponseBody responseBody = outputStream -> aiProxyService.writeStream(
                proxyResponse.bodyFlux(),
                outputStream);
        return toStreamResponse(proxyResponse, responseBody);
    }

    private ResponseEntity<byte[]> toByteResponse(ProxyByteResponse proxyResponse) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());
        return ResponseEntity.status(proxyResponse.status())
                .headers(headers)
                .body(proxyResponse.body());
    }

    private ResponseEntity<StreamingResponseBody> toStreamResponse(
            ProxyStreamResponse proxyResponse,
            StreamingResponseBody responseBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());
        return ResponseEntity.status(proxyResponse.status())
                .headers(headers)
                .body(responseBody);
    }

    private ResponseEntity<StreamingResponseBody> toProxyErrorStreamResponse(ProxyStreamResponse proxyResponse) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(proxyResponse.headers());
        byte[] errorBody = proxyResponse.errorBody() != null ? proxyResponse.errorBody() : new byte[0];
        StreamingResponseBody responseBody = outputStream -> {
            outputStream.write(errorBody);
            outputStream.flush();
        };
        return ResponseEntity.status(proxyResponse.status())
                .headers(headers)
                .body(responseBody);
    }
}
