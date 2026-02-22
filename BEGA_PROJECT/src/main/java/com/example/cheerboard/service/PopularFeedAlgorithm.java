package com.example.cheerboard.service;

import java.util.Locale;

/**
 * 인기 피드 정렬 알고리즘 타입.
 */
public enum PopularFeedAlgorithm {
    TIME_DECAY,
    ENGAGEMENT_RATE,
    HYBRID;

    public static PopularFeedAlgorithm from(String raw) {
        if (raw == null || raw.isBlank()) {
            return HYBRID;
        }
        try {
            return PopularFeedAlgorithm.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HYBRID;
        }
    }
}
