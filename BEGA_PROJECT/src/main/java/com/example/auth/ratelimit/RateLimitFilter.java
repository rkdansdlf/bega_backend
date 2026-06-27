package com.example.auth.ratelimit;

import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.common.dto.ApiResponse;
import com.example.common.ratelimit.RateLimitService;
import com.example.common.web.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * [Security Fix - High #1] 인증 엔드포인트와 공개 텔레메트리에 대한 IP 기반 Rate Limiting.
 * A04 — Insecure Design / CWE-307 (Improper Restriction of Excessive Authentication Attempts)
 *
 * <p>기존 Redis 기반 RateLimitService를 사용해 다중 인스턴스에서도 동일하게 동작한다.</p>
 *
 * <p>보호 대상:
 * <ul>
 *   <li>POST /api/auth/login — 3 req/min per IP</li>
 *   <li>POST /api/auth/reissue — 20 req/min per IP</li>
 *   <li>POST /api/auth/signup — 3 req/hour per IP</li>
 *   <li>POST /api/auth/password-reset/request — 3 req/hour per IP</li>
 *   <li>POST /api/auth/password/reset/request — 3 req/hour per IP (legacy alias)</li>
 *   <li>POST /api/client-errors — 120 req/min per IP</li>
 *   <li>POST /api/client-errors/feedback — 30 req/min per IP</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("POST", "/api/auth/login", "auth:login", 3, 60),
            new RateLimitRule("POST", "/api/auth/reissue", "auth:reissue", 20, 60),
            new RateLimitRule("POST", "/api/auth/signup", "auth:signup", 3, 3600),
            new RateLimitRule("POST", "/api/auth/password-reset/request", "auth:password-reset-request", 3, 3600),
            new RateLimitRule("POST", "/api/auth/password/reset/request", "auth:password-reset-request", 3, 3600),
            new RateLimitRule("POST", "/api/client-errors", "telemetry:client-error", 120, 60),
            new RateLimitRule("POST", "/api/client-errors/feedback", "telemetry:client-feedback", 30, 60),
            new RateLimitRule("POST", "/api/dm/messages", "dm:send", 60, 60),
            new RateLimitRule("DELETE", "/api/dm/messages/", "dm:delete", 30, 60, true)
    );

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final AuthSecurityMonitoringService authSecurityMonitoringService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            AuthSecurityMonitoringService authSecurityMonitoringService,
            ObjectMapper objectMapper) {
        this.rateLimitService = Objects.requireNonNull(rateLimitService);
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver);
        this.authSecurityMonitoringService = Objects.requireNonNull(authSecurityMonitoringService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitRule rule = matchRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String key = rule.redisKey(clientIp);
        if (rateLimitService.isAllowed(key, rule.limit, rule.windowSeconds, true)) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1L, rule.windowSeconds);
        if (rule.recordsAuthMonitoring()) {
            authSecurityMonitoringService.recordAuthRateLimitReject();
        }
        log.warn("Rate limit exceeded path={} ip={} key={} retryAfter={}s",
                rule.path, clientIp, key, retryAfterSeconds);
        writeTooManyRequests(response, retryAfterSeconds);
    }

    private RateLimitRule matchRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        for (RateLimitRule rule : RULES) {
            if (rule.matches(method, path)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolveOrUnknown(request);
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(
                        "RATE_LIMITED",
                        "너무 많은 요청을 보냈습니다. 잠시 후 다시 시도해주세요.",
                        Map.of("retryAfterSeconds", retryAfterSeconds)));
    }

    private record RateLimitRule(String method, String path, String ruleKey, int limit, int windowSeconds, boolean prefixMatch) {
        RateLimitRule(String method, String path, String ruleKey, int limit, int windowSeconds) {
            this(method, path, ruleKey, limit, windowSeconds, false);
        }

        RateLimitRule {
            Objects.requireNonNull(method);
            Objects.requireNonNull(path);
            Objects.requireNonNull(ruleKey);
            HttpMethod.valueOf(method);
        }

        boolean matches(String requestMethod, String requestPath) {
            if (!method.equalsIgnoreCase(requestMethod)) {
                return false;
            }
            return prefixMatch ? requestPath.startsWith(path) : path.equals(requestPath);
        }

        String redisKey(String clientIp) {
            return String.format("rate:limit:%s:%s:%s:%s", ruleKey, method, path, clientIp);
        }

        boolean recordsAuthMonitoring() {
            return ruleKey.startsWith("auth:");
        }
    }
}
