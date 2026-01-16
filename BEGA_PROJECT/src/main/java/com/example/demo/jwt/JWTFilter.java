package com.example.demo.jwt;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

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

public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;

    // ✅ UserService 제거 (더 이상 필요 없음!)
    public JWTFilter(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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

            // 허용된 도메인 리스트 (프로덕션에서는 환경변수나 설정파일로 관리 권장)
            String allowedDomain = "http://localhost:3000";
            String allowedBackend = "http://localhost:8080";

            boolean isValidRef = (referer != null
                    && (referer.startsWith(allowedDomain) || referer.startsWith(allowedBackend)));
            boolean isValidOrigin = (origin != null && (origin.equals(allowedDomain) || origin.equals(allowedBackend)));

            if (!isValidRef && !isValidOrigin) {
                // Referer나 Origin이 없거나 허용되지 않은 도메인이면 차단
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("CSRF Protection: Invalid Referer/Origin");
                return;
            }
        }

        String token = authorization;

        // 토큰 소멸 시간 검증
        if (jwtUtil.isExpired(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ JWT에서 필요한 정보 모두 추출 (캐싱 적용, DB 조회 없음!)
        try {
            // String email = jwtUtil.getEmail(token);
            String role = jwtUtil.getRole(token);
            Long userId = jwtUtil.getUserId(token);

            if (userId == null) {
                filterChain.doFilter(request, response);
                return;
            }

            // ✅ DB 조회 없이 Authentication 객체 생성
            Collection<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    userId, // Principal로 설정
                    null,
                    authorities);

            // 사용자 등록
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
        }

        filterChain.doFilter(request, response);
    }
}