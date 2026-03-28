package com.example.ai.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAssistantChatMessageRequest(
        @NotBlank(message = "content는 필수입니다.")
        @Size(max = 12000, message = "content는 최대 12000자까지 허용됩니다.")
        String content,
        String status,
        Boolean verified,
        Boolean cached,
        @Size(max = 100, message = "intent는 최대 100자까지 허용됩니다.")
        String intent,
        @Size(max = 100, message = "strategy는 최대 100자까지 허용됩니다.")
        String strategy,
        @Size(max = 50, message = "finishReason은 최대 50자까지 허용됩니다.")
        String finishReason,
        Boolean cancelled,
        @Size(max = 100, message = "errorCode는 최대 100자까지 허용됩니다.")
        String errorCode,
        @Size(max = 50, message = "plannerMode는 최대 50자까지 허용됩니다.")
        String plannerMode,
        Boolean plannerCacheHit,
        @Size(max = 50, message = "toolExecutionMode는 최대 50자까지 허용됩니다.")
        String toolExecutionMode,
        @Size(max = 100, message = "fallbackReason은 최대 100자까지 허용됩니다.")
        String fallbackReason,
        JsonNode metadata,
        JsonNode citations,
        JsonNode toolCalls) {
}
