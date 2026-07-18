package com.example.ai.exception;

import com.example.ai.config.AiProxyRequestLimits;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class AiProxyPayloadTooLargeException extends AiProxyException {

    private final long maxBytes;

    public AiProxyPayloadTooLargeException(long maxBytes) {
        super(
                HttpStatus.CONTENT_TOO_LARGE,
                AiProxyRequestLimits.PAYLOAD_TOO_LARGE_CODE,
                "AI 요청 본문이 너무 큽니다.",
                Map.of("maxBytes", maxBytes));
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
