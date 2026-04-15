package com.example.auth.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.web.util.UriUtils;

public final class HandleNormalizer {

    private HandleNormalizer() {
    }

    public static String normalize(String handle) {
        if (handle == null) {
            return null;
        }

        String normalized = decode(handle.trim());
        if (!normalized.startsWith("@")) {
            normalized = "@" + normalized;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static List<String> candidates(String handle) {
        String normalized = normalize(handle);
        if (normalized == null || normalized.isBlank()) {
            return List.of();
        }

        String legacy = legacy(normalized);
        if (legacy.equals(normalized)) {
            return List.of(normalized);
        }

        return List.of(normalized, legacy);
    }

    public static String legacy(String handle) {
        if (handle == null) {
            return null;
        }

        String normalized = decode(handle.trim());
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String decode(String handle) {
        try {
            return UriUtils.decode(handle, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return handle;
        }
    }
}
