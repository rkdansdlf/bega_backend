package com.example.demo.security;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException; 

import com.fasterxml.jackson.databind.ObjectMapper;


public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository; 
    private final ObjectMapper objectMapper = new ObjectMapper(); 

    public LoginFilter(AuthenticationManager authenticationManager, JWTUtil jwtUtil, RefreshRepository refreshRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        
        // 필터가 처리할 URL을 명시
        setFilterProcessesUrl("/api/auth/login"); 
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException("인증 방법이 지원되지 않습니다: " + request.getMethod());
        }

        String identifier = null; // email 또는 username으로 사용될 변수
        String password = null;

        // JSON 요청 본문 파싱
        if (request.getContentType() != null && request.getContentType().contains("application/json")) {
            try (InputStream is = request.getInputStream()) {
                // JSON에서 로그인 데이터 추출 
                Map<String, String> loginData = objectMapper.readValue(is, Map.class);
                
                identifier = loginData.get("email"); 
                password = loginData.get("password");
                
            } catch (IOException e) {
                // 스트림 읽기 실패 또는 JSON 형식 오류
                throw new AuthenticationServiceException("로그인 요청 본문 형식(예상 JSON)이 잘못되었거나 스트림을 읽지 못했습니다.", e);
            }
        } else {
            // Content-Type이 JSON이 아닌 경우 (폼 데이터 등)
            identifier = obtainUsername(request);
            password = obtainPassword(request);
        }
        
        // 유효성 검사
        if (identifier == null || identifier.trim().isEmpty() || password == null || password.trim().isEmpty()) {
             throw new AuthenticationServiceException("Email and password must be provided.");
        }
        
        // 추출된 Email을 Spring Security의 principal (username)으로 전달
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(identifier, password, null);

        return authenticationManager.authenticate(authToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) throws IOException, ServletException {



        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = customUserDetails.getUsername(); 

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();
        Long userId = customUserDetails.getId();
        // Access Token 유효 기간 설정 
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; //2시간

        // JWT 생성
        String accessToken = jwtUtil.createJwt(email, role, userId, accessTokenExpiredMs);
        String refreshToken = jwtUtil.createRefreshToken(email, role, userId);

        // Refresh Token DB 저장/업데이트
        RefreshToken existToken = refreshRepository.findByEmail(email);

        if (existToken == null) {
            RefreshToken newRefreshToken = new RefreshToken();
            newRefreshToken.setEmail(email); 
            newRefreshToken.setToken(refreshToken);
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); 
            
            refreshRepository.save(newRefreshToken);

        } else {
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }
        
        // 쿠키에 Access/Refresh Token 동시 추가
        int accessTokenMaxAge = (int)(accessTokenExpiredMs / 1000);
        addSameSiteCookie(response, "Authorization", accessToken, accessTokenMaxAge);
        
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000);
        addSameSiteCookie(response, "Refresh", refreshToken, refreshTokenMaxAge);


        // 200 OK 응답으로 REST API 호출을 종료합니다.
        response.setStatus(HttpServletResponse.SC_OK);
        // 클라이언트에 성공 메시지 전송
        response.setContentType("application/json;charset=UTF-8");

        // JSON 응답 생성 (role 포함)
        String jsonResponse = String.format(
            "{\"success\": true, \"message\": null, \"data\": {\"accessToken\": \"%s\", \"name\": \"%s\", \"role\": \"%s\"}}",
            accessToken,
            email,
            role
        );

        response.getWriter().write(jsonResponse);
        response.getWriter().flush();

    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        // 인증 실패 시 401 Unauthorized 응답
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\": \"Login Failed\", \"message\": \"" + failed.getMessage() + "\"}");
        response.getWriter().flush();
    }
    
    // SameSite=Lax를 강제 적용하여 쿠키를 헤더에 직접 추가합니다.
    private void addSameSiteCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        // HttpOnly: true, Path: / (모든 경로), SameSite: Lax (다른 포트 요청 허용)
        String cookieString = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=None; Secure", 
                                            name, value, maxAgeSeconds);
        response.addHeader("Set-Cookie", cookieString);
    }
    
}
