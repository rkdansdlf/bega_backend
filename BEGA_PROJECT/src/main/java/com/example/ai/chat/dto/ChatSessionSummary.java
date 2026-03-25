package com.example.ai.chat.dto;

import java.time.Instant;

public record ChatSessionSummary(
        Long sessionId,
        String title,
        Integer messageCount,
        String latestMessagePreview,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt) {
}
