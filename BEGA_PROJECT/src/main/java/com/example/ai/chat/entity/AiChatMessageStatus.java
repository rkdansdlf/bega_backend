package com.example.ai.chat.entity;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum AiChatMessageStatus {
    COMPLETED,
    CANCELLED,
    ERROR;

    public static AiChatMessageStatus fromRequest(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return COMPLETED;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        for (AiChatMessageStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 메시지 상태입니다: " + rawValue);
    }
}
