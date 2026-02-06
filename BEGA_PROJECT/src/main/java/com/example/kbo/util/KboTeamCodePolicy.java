package com.example.kbo.util;

import java.util.Set;

public final class KboTeamCodePolicy {

    public static final Set<String> CANONICAL_CODES = Set.of(
            "SS", "LT", "LG", "OB", "HT", "WO", "HH", "SSG", "NC", "KT");

    private KboTeamCodePolicy() {
    }

    public static boolean isCanonicalTeamCode(String teamCode) {
        String normalized = TeamCodeNormalizer.normalize(teamCode);
        return normalized != null && CANONICAL_CODES.contains(normalized);
    }
}
