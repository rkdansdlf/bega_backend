package com.example.kbo.util;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TeamCodeResolver {

    private static final Set<String> CANONICAL_CODES = Set.of(
            "SS", "LT", "LG", "DB", "KIA", "KH", "HH", "SSG", "NC", "KT");

    private static final Map<String, String> INPUT_TO_CANONICAL = new LinkedHashMap<>();
    private static final Map<String, List<String>> CANONICAL_TO_VARIANTS = new LinkedHashMap<>();
    private static final Map<String, String> CANONICAL_TO_NAME = new LinkedHashMap<>();
    private static final Map<String, List<BrandHistoryEntry>> BRAND_HISTORY = new LinkedHashMap<>();
    private static final int DEFAULT_CANONICAL_WINDOW_START = 2021;
    private static final int DEFAULT_CANONICAL_WINDOW_END = 2025;
    private static final String DEFAULT_READ_MODE = "canonical_only";
    private static final String DEFAULT_OUTSIDE_WINDOW_MODE = "dual";

    private record BrandHistoryEntry(Integer endYear, String brandCode) {}

    static {
        // Brand history mapping
        BRAND_HISTORY.put("KIA", List.of(new BrandHistoryEntry(2000, "HT"), new BrandHistoryEntry(null, "KIA")));
        BRAND_HISTORY.put("SSG", List.of(new BrandHistoryEntry(2020, "SK"), new BrandHistoryEntry(null, "SSG")));
        BRAND_HISTORY.put("DB", List.of(new BrandHistoryEntry(1998, "OB"), new BrandHistoryEntry(null, "DB")));
        BRAND_HISTORY.put("KH", List.of(new BrandHistoryEntry(2009, "WO"), new BrandHistoryEntry(2018, "NX"), new BrandHistoryEntry(null, "KH")));
        BRAND_HISTORY.put("HH", List.of(new BrandHistoryEntry(1993, "BE"), new BrandHistoryEntry(null, "HH")));
        BRAND_HISTORY.put("LG", List.of(new BrandHistoryEntry(1989, "MBC"), new BrandHistoryEntry(null, "LG")));

        // Canonical + code aliases
        putCanonical("KIA", "KIA", "HT", "해태");
        putCanonical("DB", "DB", "OB", "DO");
        putCanonical("KH", "KH", "WO", "NX", "KI", "KW");
        putCanonical("SSG", "SSG", "SK");
        putCanonical("HH", "HH", "BE", "빙그레");
        putCanonical("LG", "LG", "MBC");
        putCanonical("LT", "LT", "LOT");
        putCanonical("SS", "SS");
        putCanonical("NC", "NC");
        putCanonical("KT", "KT");

        // Korean names and common aliases
        putNameAliases("KIA", "기아", "KIA 타이거즈", "기아 타이거즈", "타이거즈", "해태 타이거즈");
        putNameAliases("DB", "두산", "두산 베어스", "베어스", "OB 베어스");
        putNameAliases("KH", "키움", "키움 히어로즈", "넥센", "넥센 히어로즈", "히어로즈", "우리", "우리 히어로즈", "넥센");
        putNameAliases("SSG", "SSG 랜더스", "SK 와이번스", "랜더스", "와이번스");
        putNameAliases("HH", "한화", "한화 이글스", "이글스", "빙그레 이글스");
        putNameAliases("LG", "LG 트윈스", "트윈스", "MBC 청룡");
        putNameAliases("LT", "롯데", "롯데 자이언츠", "자이언츠");
        putNameAliases("SS", "삼성", "삼성 라이온즈", "라이온즈");
        putNameAliases("NC", "NC 다이노스", "다이노스");
        putNameAliases("KT", "KT 위즈", "위즈");

        CANONICAL_TO_NAME.put("KIA", "KIA 타이거즈");
        CANONICAL_TO_NAME.put("HT", "해태 타이거즈");
        CANONICAL_TO_NAME.put("DB", "두산 베어스");
        CANONICAL_TO_NAME.put("OB", "OB 베어스");
        CANONICAL_TO_NAME.put("KH", "키움 히어로즈");
        CANONICAL_TO_NAME.put("NX", "넥센 히어로즈");
        CANONICAL_TO_NAME.put("WO", "우리 히어로즈");
        CANONICAL_TO_NAME.put("SSG", "SSG 랜더스");
        CANONICAL_TO_NAME.put("SK", "SK 와이번스");
        CANONICAL_TO_NAME.put("HH", "한화 이글스");
        CANONICAL_TO_NAME.put("BE", "빙그레 이글스");
        CANONICAL_TO_NAME.put("LG", "LG 트윈스");
        CANONICAL_TO_NAME.put("MBC", "MBC 청룡");
        CANONICAL_TO_NAME.put("LT", "롯데 자이언츠");
        CANONICAL_TO_NAME.put("SS", "삼성 라이온즈");
        CANONICAL_TO_NAME.put("NC", "NC 다이노스");
        CANONICAL_TO_NAME.put("KT", "KT 위즈");
    }

    private TeamCodeResolver() {
    }

    private static void putCanonical(String canonical, String... aliases) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(canonical);
        for (String alias : aliases) {
            variants.add(alias);
            INPUT_TO_CANONICAL.put(alias, canonical);
            INPUT_TO_CANONICAL.put(alias.toUpperCase(), canonical);
        }
        CANONICAL_TO_VARIANTS.put(canonical, List.copyOf(variants));
    }

    private static void putNameAliases(String canonical, String... aliases) {
        for (String alias : aliases) {
            INPUT_TO_CANONICAL.put(alias, canonical);
            INPUT_TO_CANONICAL.put(alias.toUpperCase(), canonical);
        }
    }

    public static String resolveCanonical(String input) {
        return resolveCanonical(input, null);
    }

    public static String resolveCanonical(String input, Integer year) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String upper = trimmed.toUpperCase();
        String canonical = INPUT_TO_CANONICAL.get(upper);

        if (canonical == null) {
            String normalizedCode = TeamCodeNormalizer.normalize(upper);
            if (normalizedCode != null && CANONICAL_CODES.contains(normalizedCode)) {
                canonical = normalizedCode;
            } else {
                canonical = upper;
            }
        }

        // Season-aware brand resolution
        if (year != null && BRAND_HISTORY.containsKey(canonical)) {
            for (BrandHistoryEntry entry : BRAND_HISTORY.get(canonical)) {
                if (entry.endYear() == null || year <= entry.endYear()) {
                    return entry.brandCode();
                }
            }
        }

        return canonical;
    }

    public static Set<String> resolveVariants(String input) {
        return resolveVariants(input, null);
    }

    public static Set<String> resolveVariants(String input, Integer year) {
        return resolveQueryVariants(input, year);
    }

    public static Set<String> resolveQueryVariants(String input, Integer year) {
        String canonical = resolveCanonical(input, null);
        if (canonical == null) {
            return Set.of();
        }

        String effectiveMode = resolveEffectiveReadMode(year);
        if ("canonical_only".equals(effectiveMode)) {
            return Set.of(resolveCanonical(input, year));
        }

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (year != null) {
            ordered.add(resolveCanonical(input, year));
        }

        List<String> variants = CANONICAL_TO_VARIANTS.get(canonical);
        if (variants != null && !variants.isEmpty()) {
            ordered.addAll(variants);
        } else {
            ordered.add(canonical);
        }
        return ordered;
    }

    public static boolean isInCanonicalWindow(int year) {
        int start = resolveCanonicalWindowStart();
        int end = resolveCanonicalWindowEnd();
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        return year >= min && year <= max;
    }

    public static String getDisplayName(String input) {
        if (input == null) return null;
        String upper = input.trim().toUpperCase();
        return CANONICAL_TO_NAME.getOrDefault(upper, input);
    }

    public static boolean isCanonical(String input) {
        String canonical = resolveCanonical(input, null);
        return canonical != null && CANONICAL_CODES.contains(canonical);
    }

    public static String getQueryMode() {
        return resolveReadMode();
    }

    public static String getQueryMode(Integer year) {
        return resolveEffectiveReadMode(year);
    }

    private static String resolveReadMode() {
        String fromProperty = System.getProperty("team.code.read.mode");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim().toLowerCase();
        }
        String fromEnv = System.getenv("TEAM_CODE_READ_MODE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim().toLowerCase();
        }
        return DEFAULT_READ_MODE;
    }

    private static String resolveEffectiveReadMode(Integer year) {
        String readMode = resolveReadMode();
        if (!"canonical_only".equals(readMode)) {
            return "dual";
        }
        if (year == null) {
            return "dual";
        }
        if (isInCanonicalWindow(year)) {
            return "canonical_only";
        }
        return resolveOutsideWindowMode();
    }

    private static String resolveOutsideWindowMode() {
        String fromProperty = System.getProperty("team.code.outside.window.mode");
        if (fromProperty != null && !fromProperty.isBlank()) {
            String normalized = fromProperty.trim().toLowerCase();
            if ("canonical_only".equals(normalized) || "dual".equals(normalized)) {
                return normalized;
            }
        }
        String fromEnv = System.getenv("TEAM_CODE_OUTSIDE_WINDOW_MODE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            String normalized = fromEnv.trim().toLowerCase();
            if ("canonical_only".equals(normalized) || "dual".equals(normalized)) {
                return normalized;
            }
        }
        return DEFAULT_OUTSIDE_WINDOW_MODE;
    }

    private static int resolveCanonicalWindowStart() {
        return resolveIntSetting(
                "team.code.canonical.window.start",
                "TEAM_CODE_CANONICAL_WINDOW_START",
                DEFAULT_CANONICAL_WINDOW_START);
    }

    private static int resolveCanonicalWindowEnd() {
        return resolveIntSetting(
                "team.code.canonical.window.end",
                "TEAM_CODE_CANONICAL_WINDOW_END",
                DEFAULT_CANONICAL_WINDOW_END);
    }

    private static int resolveIntSetting(String propertyName, String envName, int defaultValue) {
        String fromProperty = System.getProperty(propertyName);
        if (fromProperty != null && !fromProperty.isBlank()) {
            try {
                return Integer.parseInt(fromProperty.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            try {
                return Integer.parseInt(fromEnv.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
