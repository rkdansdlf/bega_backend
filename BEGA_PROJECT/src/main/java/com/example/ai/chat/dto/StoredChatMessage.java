package com.example.ai.chat.dto;

import com.example.ai.chat.entity.AiChatMessageRole;
import com.example.ai.chat.entity.AiChatMessageStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record StoredChatMessage(
        Long messageId,
        Long sessionId,
        AiChatMessageRole role,
        AiChatMessageStatus status,
        String content,
        Boolean verified,
        Boolean cached,
        String intent,
        String strategy,
        String finishReason,
        boolean cancelled,
        String errorCode,
        String plannerMode,
        Boolean plannerCacheHit,
        String toolExecutionMode,
        String fallbackReason,
        JsonNode metadata,
        JsonNode citations,
        JsonNode toolCalls,
        boolean favorite,
        Instant createdAt,
        Instant updatedAt) {
}
