package com.example.common.clienterror;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

final class ClientErrorSupport {

    static final ZoneOffset UTC = ZoneOffset.UTC;
    static final int STACK_LOG_LIMIT = 4000;
    static final int MESSAGE_LOG_LIMIT = 1000;
    static final int ROUTE_LOG_LIMIT = 500;
    static final int ENDPOINT_LOG_LIMIT = 500;
    static final int COMMENT_LOG_LIMIT = 2000;
    static final int METRIC_TAG_LIMIT = 120;

    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)");
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("(?<=/)\\d+(?=/|$)");

    private ClientErrorSupport() {
    }

    static String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .trim();

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    static String normalizeRoute(String route) {
        return normalizePath(route, true);
    }

    static String normalizeEndpoint(String endpoint) {
        return normalizePath(endpoint, false);
    }

    private static String normalizePath(String value, boolean defaultRoot) {
        if (value == null || value.isBlank()) {
            return defaultRoot ? "/" : null;
        }

        String normalized = value.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }

        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }

        if (normalized.isBlank()) {
            return defaultRoot ? "/" : null;
        }

        normalized = normalized.replaceAll("/{2,}", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = UUID_PATH_SEGMENT.matcher(normalized).replaceAll(":uuid");
        normalized = NUMERIC_PATH_SEGMENT.matcher(normalized).replaceAll(":id");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalizeTag(normalized, defaultRoot ? "/" : null, true);
    }

    static String normalizeStatusGroup(Integer statusCode) {
        if (statusCode == null) {
            return "none";
        }

        int family = statusCode / 100;
        return switch (family) {
            case 1, 2, 3, 4, 5 -> family + "xx";
            default -> "other";
        };
    }

    static String normalizeTag(String value, String fallback) {
        return normalizeTag(value, fallback, false);
    }

    static String normalizeTag(String value, String fallback, boolean allowRouteChars) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = allowRouteChars
                ? normalized.replaceAll("[^a-z0-9_:/-]", "_")
                : normalized.replaceAll("[^a-z0-9_-]", "_");
        normalized = normalized.replaceAll("_+", "_");

        if (normalized.isBlank()) {
            return fallback;
        }

        if (normalized.length() > METRIC_TAG_LIMIT) {
            return normalized.substring(0, METRIC_TAG_LIMIT);
        }

        return normalized;
    }

    static LocalDateTime parseOccurredAt(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now(UTC);
        }

        try {
            return OffsetDateTime.parse(timestamp).withOffsetSameInstant(UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(timestamp).atOffset(UTC).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                return LocalDateTime.now(UTC);
            }
        }
    }

    static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(UTC);
    }

    static String buildFingerprint(
            ClientErrorBucket bucket,
            String message,
            String normalizedRoute,
            Integer statusCode,
            String method,
            String normalizedEndpoint) {
        String canonical;
        if (bucket == ClientErrorBucket.API) {
            canonical = String.join("|",
                    bucket.getValue(),
                    defaultString(message),
                    defaultString(normalizedRoute),
                    statusCode == null ? "" : String.valueOf(statusCode),
                    defaultString(method),
                    defaultString(normalizedEndpoint));
        } else {
            canonical = String.join("|",
                    bucket.getValue(),
                    defaultString(message),
                    defaultString(normalizedRoute));
        }
        return sha256Hex(canonical);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
