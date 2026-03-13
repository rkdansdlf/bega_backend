package com.example.auth.util;

import java.net.URI;
import java.util.Set;

public final class FrontendRedirectUtil {

    private static final Set<String> DISALLOWED_REDIRECTS = Set.of(
            "/login",
            "/signup",
            "/oauth/callback"
    );

    private FrontendRedirectUtil() {
    }

    public static String sanitizeRedirect(String candidate) {
        if (candidate == null) {
            return null;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.contains("://")) {
            return null;
        }

        try {
            URI parsed = URI.create(trimmed);
            if (parsed.getScheme() != null || parsed.getRawAuthority() != null) {
                return null;
            }

            String path = parsed.getPath();
            if (path == null || !path.startsWith("/") || isDisallowedPath(path)) {
                return null;
            }

            StringBuilder sanitized = new StringBuilder(path);
            if (parsed.getRawQuery() != null && !parsed.getRawQuery().isBlank()) {
                sanitized.append('?').append(parsed.getRawQuery());
            }
            if (parsed.getRawFragment() != null && !parsed.getRawFragment().isBlank()) {
                sanitized.append('#').append(parsed.getRawFragment());
            }
            return sanitized.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isDisallowedPath(String path) {
        return DISALLOWED_REDIRECTS.stream()
                .anyMatch(disallowed -> path.equals(disallowed) || path.startsWith(disallowed + "/"));
    }
}
