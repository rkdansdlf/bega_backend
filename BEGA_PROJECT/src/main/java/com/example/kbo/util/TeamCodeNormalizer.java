package com.example.kbo.util;

import java.util.Map;

public final class TeamCodeNormalizer {

    private static final Map<String, String> CODE_MAP = Map.ofEntries(
            // KIA franchise
            Map.entry("HT", "KIA"),
            // Kiwoom/Heroes franchise
            Map.entry("KI", "KH"),
            Map.entry("NX", "KH"),
            Map.entry("WO", "KH"),
            Map.entry("KW", "KH"),
            // Doosan franchise
            Map.entry("DO", "DB"),
            Map.entry("OB", "DB"),
            // Hanwha legacy
            Map.entry("BE", "HH"),
            // SSG franchise
            Map.entry("SK", "SSG"),
            Map.entry("SL", "SSG"),
            Map.entry("MBC", "LG"),
            Map.entry("LOT", "LT"));

    private TeamCodeNormalizer() {
    }

    public static String normalize(String teamCode) {
        if (teamCode == null) {
            return null;
        }
        String trimmed = teamCode.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String upper = trimmed.toUpperCase();
        return CODE_MAP.getOrDefault(upper, upper);
    }
}
