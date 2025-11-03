package com.example.demo.jwt;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority; 
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.demo.service.UserService; 

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;
    private final UserService userService; 

    public JWTFilter(JWTUtil jwtUtil, UserService userService) { 
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
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
                authorization = header.substring(7); // "Bearer " 이후의 문자열(토큰 값)만 추출
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
            System.out.println("토큰이 쿠키나 헤더에 없습니다. 인증 없이 통과.");
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization;

        // 토큰 소멸 시간 검증
        if (jwtUtil.isExpired(token)) {
            System.out.println("토큰 만료");
            filterChain.doFilter(request, response);
            return;
        }

        // 인증 성공
        String email = jwtUtil.getEmail(token); 
        String role = jwtUtil.getRole(token);

        try {
            // UserService를 사용하여 이메일로 ID를 조회
            Long userId = userService.getUserIdByEmail(email);

            // 권한 생성
            Collection<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

            // userId를 Principal로 설정하는 Authentication 객체 생성
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                userId, // Principal로 설정
                null,
                authorities 
            );
            
            // 사용자 등록
            SecurityContextHolder.getContext().setAuthentication(authToken);
            System.out.println("✅ JWT 인증 성공: User ID " + userId + " 등록 완료.");

        } catch (IllegalArgumentException e) {
            System.out.println("해당 이메일로 사용자를 찾을 수 없습니다: " + email);
        }

        filterChain.doFilter(request, response);
    }
}