package com.example.ai.config;

import com.example.ai.exception.AiProxyException;
import com.example.ai.exception.AiProxyPayloadTooLargeException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AiProxyRequestLimits {

    public static final String PAYLOAD_TOO_LARGE_CODE = "AI_PROXY_PAYLOAD_TOO_LARGE";

    private final long maxChatJsonBytes;
    private final long maxCoachJsonBytes;
    private final long maxVoiceUploadBytes;
    private final long maxVoiceRequestBytes;
    private final long maxAdminJsonBytes;
    private final long maxChatPersistenceJsonBytes;

    public AiProxyRequestLimits(
            @Value("${app.ai.proxy.max-chat-json-bytes:12288}") long maxChatJsonBytes,
            @Value("${app.ai.proxy.max-coach-json-bytes:65536}") long maxCoachJsonBytes,
            @Value("${app.ai.proxy.max-voice-upload-bytes:10485760}") long maxVoiceUploadBytes,
            @Value("${app.ai.proxy.max-voice-request-bytes:10551296}") long maxVoiceRequestBytes,
            @Value("${app.ai.proxy.max-admin-json-bytes:262144}") long maxAdminJsonBytes,
            @Value("${app.ai.proxy.max-chat-persistence-json-bytes:131072}") long maxChatPersistenceJsonBytes) {
        this.maxChatJsonBytes = normalizeLimit(maxChatJsonBytes);
        this.maxCoachJsonBytes = normalizeLimit(maxCoachJsonBytes);
        this.maxVoiceUploadBytes = normalizeLimit(maxVoiceUploadBytes);
        this.maxVoiceRequestBytes = normalizeLimit(maxVoiceRequestBytes);
        this.maxAdminJsonBytes = normalizeLimit(maxAdminJsonBytes);
        this.maxChatPersistenceJsonBytes = normalizeLimit(maxChatPersistenceJsonBytes);
    }

    public Long maxBytesFor(String method, String requestUri) {
        if (!isBodyMethod(method) || requestUri == null || !requestUri.startsWith("/api/ai/")) {
            return null;
        }
        Long knownEndpointLimit = switch (requestUri) {
            case "/api/ai/chat/completion", "/api/ai/chat/stream" -> maxChatJsonBytes;
            case "/api/ai/coach/analyze" -> maxCoachJsonBytes;
            case "/api/ai/chat/voice" -> maxVoiceRequestBytes;
            default -> null;
        };
        if (knownEndpointLimit != null) {
            return knownEndpointLimit;
        }

        if (requestUri.startsWith("/api/ai/release-decision/")) {
            return maxAdminJsonBytes;
        }
        if (requestUri.startsWith("/api/ai/chat/sessions")
                || requestUri.startsWith("/api/ai/chat/favorites")) {
            return maxChatPersistenceJsonBytes;
        }

        return maxChatPersistenceJsonBytes;
    }

    public void validateChatJson(String payload) {
        validateJsonPayload(payload, maxChatJsonBytes);
    }

    public void validateCoachJson(String payload) {
        validateJsonPayload(payload, maxCoachJsonBytes);
    }

    public void validateVoiceUpload(MultipartFile file) {
        if (file != null && file.getSize() > maxVoiceUploadBytes) {
            throw payloadTooLarge(maxVoiceUploadBytes);
        }
    }

    public void validateAdminJson(String payload) {
        validateJsonPayload(payload, maxAdminJsonBytes);
    }

    private void validateJsonPayload(String payload, long maxBytes) {
        long payloadBytes = payload == null ? 0L : payload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > maxBytes) {
            throw payloadTooLarge(maxBytes);
        }
    }

    private AiProxyException payloadTooLarge(long maxBytes) {
        return new AiProxyPayloadTooLargeException(maxBytes);
    }

    private long normalizeLimit(long value) {
        return Math.max(1L, value);
    }

    private boolean isBodyMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }
}
