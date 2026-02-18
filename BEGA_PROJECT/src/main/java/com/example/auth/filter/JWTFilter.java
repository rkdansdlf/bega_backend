package com.example.auth.filter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.net.URI;
import java.util.Objects;

import org.springframework.lang.NonNull;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@lombok.extern.slf4j.Slf4j
public class JWTFilter extends OncePerRequestFilter {

    private final com.example.auth.util.JWTUtil jwtUtil;
    private final boolean isDev;
    private final List<String> allowedOrigins;
    private final com.example.auth.service.TokenBlacklistService tokenBlacklistService;

    // localhost IP 주소 목록 (Debug 헤더 허용)
    private static final List<String> LOCALHOST_IPS = List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    public JWTFilter(com.example.auth.util.JWTUtil jwtUtil, boolean isDev, List<String> allowedOrigins,
            com.example.auth.service.TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.isDev = isDev;
        this.allowedOrigins = allowedOrigins;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = null;

        // 쿠키에서 Authorization 토큰 추출 시도
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("Authorization")) {
                    authorization = cookie.getValue();
                    break;
                }
            }
        }

        // 쿠키에 없으면, Authorization 헤더에서 토큰 추출 시도
        if (authorization == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                authorization = header.substring(7);
            }
        }

        String requestUri = request.getRequestURI();
        String normalizedRequestUri = requestUri != null ? requestUri.replaceAll("/+$", "") : requestUri;

        // 인증 API 공개 경로는 필터 처리 스킵
        if (normalizedRequestUri.equals("/api/auth/login") ||
                normalizedRequestUri.equals("/api/auth/signup") ||
                normalizedRequestUri.equals("/api/auth/reissue") ||
                normalizedRequestUri.equals("/api/auth/logout") ||
                normalizedRequestUri.equals("/api/auth/password/reset/request") ||
                normalizedRequestUri.equals("/api/auth/password/reset/confirm") ||
                normalizedRequestUri.equals("/api/auth/password-reset/request") ||
                normalizedRequestUri.equals("/api/auth/password-reset/confirm")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 로그인 및 OAuth2 경로는 필터 스킵
        if (requestUri.matches("^\\/login(?:\\/.*)?$") || requestUri.matches("^\\/oauth2(?:\\/.*)?$")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 토큰이 없는 경우
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 🚨 CSRF 방지: Referer 체크 (상태 변경 요청에 대해)
        String method = request.getMethod();
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
            String referer = request.getHeader("Referer");
            String origin = request.getHeader("Origin");

            String refererOrigin = extractOrigin(referer);
            String originValue = extractOrigin(origin);

            boolean isAllowed = isAllowedOrigin(refererOrigin) || isAllowedOrigin(originValue);

            if (!isAllowed) {
                // Referer나 Origin이 없거나 허용되지 않은 도메인이면 차단
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("CSRF Protection: Invalid Referer/Origin");
                return;
            }
        }

        String token = authorization;

        // [Security Fix] 블랙리스트 확인 (로그아웃된 토큰)
        if (tokenBlacklistService != null && tokenBlacklistService.isBlacklisted(token)) {
            log.debug("Blacklisted token rejected");
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰 소멸 시간 검증
        if (jwtUtil.isExpired(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // [Security Fix] 토큰 타입 검증: access 토큰만 허용 (link, refresh 등 차단)
        // NOTE: 과거 토큰 호환성을 위해 token_type이 없는 레거시 토큰도 유효 토큰으로 취급
        String tokenType = jwtUtil.getTokenType(token);
        if (tokenType != null && !"access".equalsIgnoreCase(tokenType.trim())) {
            log.warn("{} token rejected for authentication (strict mode)", tokenType);
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ JWT에서 필요한 정보 모두 추출 (캐싱 적용, DB 조회 없음!)
        try {
            // String email = jwtUtil.getEmail(token);
            String role = jwtUtil.getRole(token);
            Long userId = jwtUtil.getUserId(token);

            // [Security Fix] 레거시 링크 토큰(claim 없음) 방지
            if ("LINK_MODE".equals(role)) {
                log.warn("Legacy Link token rejected for authentication");
                filterChain.doFilter(request, response);
                return;
            }

            if (userId == null) {
                filterChain.doFilter(request, response);
                return;
            }

            // ✅ DB 조회 없이 Authentication 객체 생성
            // [Security Fix] Dev Toggle: localhost에서만 Debug 헤더 허용
            if (isDev) {
                String debugRole = request.getHeader("X-Debug-Role");
                if (debugRole != null && !debugRole.isBlank()) {
                    String remoteAddr = request.getRemoteAddr();
                    if (LOCALHOST_IPS.contains(remoteAddr)) {
                        log.warn("Dev Mode: Role Override {} -> {} from localhost", role, debugRole);
                        role = debugRole;
                    } else {
                        log.error("Unauthorized Debug Role Override Attempt from IP: {}", remoteAddr);
                        // 외부 IP에서의 Debug 헤더 시도는 무시하고 원래 role 유지
                    }
                }
            }

            Collection<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    userId, // Principal로 설정
                    null,
                    authorities);

            // 사용자 등록
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
            // 토큰 파싱 실패 또는 만료 등 인증 실패 시 로그 출력
            log.error("JWT Authentication Failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractOrigin(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(headerValue);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (scheme == null || host == null) {
                return null;
            }

            if (port == -1) {
                return String.format("%s://%s", scheme, host);
            }
            return String.format("%s://%s:%d", scheme, host, port);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }

        for (String allowed : allowedOrigins) {
            if (matchesOriginPattern(origin, allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOriginPattern(String origin, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if (origin.equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(":*")) {
            try {
                URI originUri = URI.create(origin);
                int wildcardIndex = pattern.lastIndexOf(":*");
                String patternBase = pattern.substring(0, wildcardIndex);
                int protocolIndex = patternBase.indexOf("://");

                if (protocolIndex == -1) {
                    return false;
                }

                String scheme = patternBase.substring(0, protocolIndex);
                String host = patternBase.substring(protocolIndex + 3);
                if (host.endsWith(":")) {
                    host = host.substring(0, host.length() - 1);
                }

                return Objects.equals(originUri.getScheme(), scheme)
                        && Objects.equals(originUri.getHost(), host);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }
}
