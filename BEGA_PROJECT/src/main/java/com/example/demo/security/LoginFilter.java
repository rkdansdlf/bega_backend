package com.example.demo.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Iterator;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.demo.entity.RefreshToken;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;
import com.example.demo.service.CustomUserDetails;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException; // 🚨 추가: successfulAuthentication 메서드 throws를 위해 필요
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    public LoginFilter(AuthenticationManager authenticationManager,
    		JWTUtil jwtUtil, RefreshRepository refreshRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        String username = obtainUsername(request);
        String password = obtainPassword(request);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password, null);

        return authenticationManager.authenticate(authToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
    		FilterChain chain, Authentication authentication)throws IOException, ServletException {

        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();

        String username = customUserDetails.getUsername();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();

        String role = auth.getAuthority();

        
        // Access Token 만료 시간 (예: 2시간)
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; 

        // Access Token 생성
        String accessToken = jwtUtil.createJwt(username, role, accessTokenExpiredMs);
        
        // Refresh Token 생성 (만료 시간은 JWTUtil 내부에서 7일 등으로 설정됨)
        String refreshToken = jwtUtil.createRefreshToken(username, role);

        // 4. Refresh Token DB 저장/업데이트
        RefreshToken existToken = refreshRepository.findByUsername(username);

        if (existToken == null) {
            // 신규 사용자 또는 첫 로그인 시
            RefreshToken newRefreshToken = new RefreshToken();
            newRefreshToken.setUsername(username);
            newRefreshToken.setToken(refreshToken);
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); 
            
            refreshRepository.save(newRefreshToken);

        } else {
            // 기존 토큰이 있을 경우, 새로운 토큰으로 업데이트
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }
        
        // 5. 쿠키에 Access/Refresh Token
        
        // Access Token 쿠키 (2시간)
        response.addCookie(createCookie("Authorization", accessToken, (int)(accessTokenExpiredMs / 1000)));
        
        // Refresh Token 쿠키 (7일)
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000);
        response.addCookie(createCookie("Refresh", refreshToken, refreshTokenMaxAge));


        System.out.println("로그인 성공");
        System.out.println("--- JWT 토큰 발행 성공 (일반 로그인) ---");
        System.out.println("발행된 Access Token: " + accessToken.substring(0, 10) + "...");
        System.out.println("Refresh Token (DB 저장됨): " + refreshToken.substring(0, 10) + "...");
        System.out.println("토큰 사용자: " + username);
        System.out.println("권한: "+ role);
        System.out.println("-------------------------------------");
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) {

        response.setStatus(401);
        System.out.println("fail");
    }
    
    //6. CustomSuccessHandler에서 사용한 쿠키 생성 헬퍼 메서드를 복사하여 추가
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds); // 초 단위로 설정
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); // HTTPS 환경에서 사용

        return cookie;
    }
}


  