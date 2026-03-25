package com.example.ai.chat.dto;

import java.time.Instant;

public record ChatFavoriteItem(
        Long messageId,
        Long sessionId,
        String sessionTitle,
        String content,
        String prompt,
        Instant favoritedAt,
        Instant messageCreatedAt) {
}
