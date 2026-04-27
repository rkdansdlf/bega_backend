package com.example.auth.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * [Security Fix - High #1] 인증 엔드포인트에 대한 IP 기반 Rate Limiting.
 * A04 — Insecure Design / CWE-307 (Improper Restriction of Excessive Authentication Attempts)
 *
 * <p>Bucket4j 기반 in-memory Token Bucket 알고리즘. 다중 인스턴스 배포 시
 * Redis-backed 분산 카운터로 업그레이드 필요.</p>
 *
 * <p>보호 대상:
 * <ul>
 *   <li>POST /api/auth/login — 5 req/min per IP</li>
 *   <li>POST /api/auth/signup — 3 req/min per IP</li>
 *   <li>POST /api/auth/password-reset/request — 3 req/hour per IP</li>
 *   <li>POST /api/auth/password/reset/request — 3 req/hour per IP (legacy alias)</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("POST", "/api/auth/login",
                    Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)))),
            new RateLimitRule("POST", "/api/auth/signup",
                    Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(1)))),
            new RateLimitRule("POST", "/api/auth/password-reset/request",
                    Bandwidth.classic(3, Refill.greedy(3, Duration.ofHours(1)))),
            new RateLimitRule("POST", "/api/auth/password/reset/request",
                    Bandwidth.classic(3, Refill.greedy(3, Duration.ofHours(1))))
    );

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitRule rule = matchRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String key = rule.path + "|" + clientIp;
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(rule.bandwidth).build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        log.warn("Rate limit exceeded path={} ip={} retryAfter={}s", rule.path, clientIp, retryAfterSeconds);
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
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"RATE_LIMITED\"," +
                        "\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"," +
                        "\"retryAfterSeconds\":" + retryAfterSeconds + "}");
    }

    /** 테스트에서 버킷 상태를 초기화하기 위한 훅. */
    public void resetBuckets() {
        buckets.clear();
    }

    private record RateLimitRule(String method, String path, Bandwidth bandwidth) {
        RateLimitRule {
            Objects.requireNonNull(method);
            Objects.requireNonNull(path);
            Objects.requireNonNull(bandwidth);
            HttpMethod.valueOf(method);
        }
    }
}
