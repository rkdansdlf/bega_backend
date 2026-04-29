package com.example.kbo.util;

import com.example.kbo.entity.GameSummaryEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;

public final class GameSummaryDisplayPolicy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> HIDDEN_SUMMARY_TYPES = Set.of("리뷰_WPA", "프리뷰");

    private GameSummaryDisplayPolicy() {
    }

    public static boolean hasDisplayableSummary(List<GameSummaryEntity> summaries) {
        return summaries != null && summaries.stream().anyMatch(GameSummaryDisplayPolicy::isDisplayable);
    }

    public static boolean isDisplayable(GameSummaryEntity summary) {
        if (summary == null) {
            return false;
        }

        String summaryType = normalize(summary.getSummaryType());
        if (summaryType == null || HIDDEN_SUMMARY_TYPES.contains(summaryType)) {
            return false;
        }

        return !isJsonObjectOrArrayString(summary.getDetailText())
                && (normalize(summary.getPlayerName()) != null || normalize(summary.getDetailText()) != null);
    }

    private static boolean isJsonObjectOrArrayString(String value) {
        String trimmed = normalize(value);
        if (trimmed == null) {
            return false;
        }

        boolean objectLike = trimmed.startsWith("{") && trimmed.endsWith("}");
        boolean arrayLike = trimmed.startsWith("[") && trimmed.endsWith("]");
        if (!objectLike && !arrayLike) {
            return false;
        }

        try {
            Object parsed = OBJECT_MAPPER.readValue(trimmed, Object.class);
            return parsed instanceof List || parsed instanceof java.util.Map;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
