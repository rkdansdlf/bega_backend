package com.example.common.clienterror;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.example.auth.service.CustomUserDetails;
import com.example.common.clienterror.dto.ClientErrorEventRequest;
import com.example.common.clienterror.dto.ClientErrorFeedbackRequest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientErrorLoggingService {

    private static final int STACK_LOG_LIMIT = 4000;
    private static final int MESSAGE_LOG_LIMIT = 1000;
    private static final int ROUTE_LOG_LIMIT = 500;
    private static final int ENDPOINT_LOG_LIMIT = 500;
    private static final int COMMENT_LOG_LIMIT = 2000;
    private static final int METRIC_TAG_LIMIT = 120;
    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)");
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("(?<=/)\\d+(?=/|$)");

    private final MeterRegistry meterRegistry;

    public void logClientError(ClientErrorEventRequest request, Authentication authentication) {
        Long authenticatedUserId = extractUserId(authentication);

        recordClientErrorMetric(request);

        log.info(
                "event=frontend_client_error eventId={} category={} route={} statusCode={} responseCode={} method={} endpoint={} userId={} sessionId={} message={}",
                sanitize(request.eventId(), 64),
                sanitize(request.category(), 64),
                sanitize(request.route(), ROUTE_LOG_LIMIT),
                request.statusCode(),
                sanitize(request.responseCode(), 64),
                sanitize(request.method(), 16),
                sanitize(request.endpoint(), ENDPOINT_LOG_LIMIT),
                authenticatedUserId,
                sanitize(request.sessionId(), 128),
                sanitize(request.message(), MESSAGE_LOG_LIMIT));

        if (request.stack() != null || request.componentStack() != null) {
            log.info(
                    "event=frontend_client_error_stack eventId={} stack={} componentStack={}",
                    sanitize(request.eventId(), 64),
                    sanitize(request.stack(), STACK_LOG_LIMIT),
                    sanitize(request.componentStack(), STACK_LOG_LIMIT));
        }
    }

    public void logClientFeedback(ClientErrorFeedbackRequest request, Authentication authentication) {
        Long authenticatedUserId = extractUserId(authentication);

        recordClientFeedbackMetric(request);

        log.info(
                "event=frontend_client_feedback eventId={} actionTaken={} route={} userId={} comment={}",
                sanitize(request.eventId(), 64),
                sanitize(request.actionTaken(), 64),
                sanitize(request.route(), ROUTE_LOG_LIMIT),
                authenticatedUserId,
                sanitize(request.comment(), COMMENT_LOG_LIMIT));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getId();
        }

        return null;
    }

    private void recordClientErrorMetric(ClientErrorEventRequest request) {
        Counter.builder("frontend_client_errors_total")
                .description("Browser-reported frontend client error events")
                .tag("category", normalizeTag(request.category(), "unknown"))
                .tag("route", normalizeRouteTag(request.route()))
                .tag("status_group", normalizeStatusGroup(request.statusCode()))
                .register(meterRegistry)
                .increment();
    }

    private void recordClientFeedbackMetric(ClientErrorFeedbackRequest request) {
        Counter.builder("frontend_client_feedback_total")
                .description("Browser-submitted frontend error feedback events")
                .tag("route", normalizeRouteTag(request.route()))
                .tag("action_taken", normalizeTag(request.actionTaken(), "unknown"))
                .register(meterRegistry)
                .increment();
    }

    private String normalizeRouteTag(String route) {
        if (route == null || route.isBlank()) {
            return "unknown";
        }

        String normalized = route.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }

        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }

        if (normalized.isBlank()) {
            return "/";
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

        return normalizeTag(normalized, "/", true);
    }

    private String normalizeStatusGroup(Integer statusCode) {
        if (statusCode == null) {
            return "none";
        }

        int family = statusCode / 100;
        return switch (family) {
            case 1, 2, 3, 4, 5 -> family + "xx";
            default -> "other";
        };
    }

    private String normalizeTag(String value, String fallback) {
        return normalizeTag(value, fallback, false);
    }

    private String normalizeTag(String value, String fallback, boolean allowRouteChars) {
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

    private String sanitize(String value, int maxLength) {
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
}
