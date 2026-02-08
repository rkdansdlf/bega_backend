package com.example.kbo.util;

import java.util.Map;

public final class TeamCodeNormalizer {

    private static final Map<String, String> CODE_MAP = Map.ofEntries(
            Map.entry("KIA", "HT"),
            Map.entry("KI", "WO"),
            Map.entry("NX", "WO"),
            Map.entry("DO", "OB"),
            Map.entry("BE", "HH"),
            Map.entry("SK", "SSG"),
            Map.entry("SL", "SSG"),
            Map.entry("MBC", "LG"),
            Map.entry("LOT", "LT")
    );

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
