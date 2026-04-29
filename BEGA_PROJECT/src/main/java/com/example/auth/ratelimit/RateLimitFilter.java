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
 * [Security Fix - High #1] 인증 엔드포인트에 대한 IP 기반 Rate Limiting.
 * A04 — Insecure Design / CWE-307 (Improper Restriction of Excessive Authentication Attempts)
 *
 * <p>기존 Redis 기반 RateLimitService를 사용해 다중 인스턴스에서도 동일하게 동작한다.</p>
 *
 * <p>보호 대상:
 * <ul>
 *   <li>POST /api/auth/login — 3 req/min per IP</li>
 *   <li>POST /api/auth/signup — 3 req/hour per IP</li>
 *   <li>POST /api/auth/password-reset/request — 3 req/hour per IP</li>
 *   <li>POST /api/auth/password/reset/request — 3 req/hour per IP (legacy alias)</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("POST", "/api/auth/login", "auth:login", 3, 60),
            new RateLimitRule("POST", "/api/auth/signup", "auth:signup", 3, 3600),
            new RateLimitRule("POST", "/api/auth/password-reset/request", "auth:password-reset-request", 3, 3600),
            new RateLimitRule("POST", "/api/auth/password/reset/request", "auth:password-reset-request", 3, 3600)
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
        authSecurityMonitoringService.recordAuthRateLimitReject();
        log.warn("Rate limit exceeded path={} ip={} key={} retryAfter={}s",
                rule.path, clientIp, key, retryAfterSeconds);
        writeTooManyRequests(response, retryAfterSeconds);
    }

    private RateLimitRule matchRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        for (RateLimitRule rule : RULES) {
            if (rule.method.equalsIgnoreCase(method) && rule.path.equals(path)) {
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

    private record RateLimitRule(String method, String path, String ruleKey, int limit, int windowSeconds) {
        RateLimitRule {
            Objects.requireNonNull(method);
            Objects.requireNonNull(path);
            Objects.requireNonNull(ruleKey);
            HttpMethod.valueOf(method);
        }

        String redisKey(String clientIp) {
            return String.format("rate:limit:%s:%s:%s:%s", ruleKey, method, path, clientIp);
        }
    }
}
