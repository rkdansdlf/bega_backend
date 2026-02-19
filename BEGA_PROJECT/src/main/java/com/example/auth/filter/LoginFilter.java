package com.example.auth.filter;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.auth.entity.RefreshToken;
import com.example.auth.util.JWTUtil;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.service.CustomUserDetails;

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

    public LoginFilter(AuthenticationManager authenticationManager, JWTUtil jwtUtil,
            RefreshRepository refreshRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;

        // 필터가 처리할 URL을 명시
        setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        if (!request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException("인증 방법이 지원되지 않습니다: " + request.getMethod());
        }

        String identifier = null; // email 또는 username으로 사용될 변수
        String password = null;

        // JSON 요청 본문 파싱
        if (request.getContentType() != null && request.getContentType().contains("application/json")) {
            try (InputStream is = request.getInputStream()) {
                // JSON에서 로그인 데이터 추출
                Map<String, String> loginData = objectMapper.readValue(is,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                        });

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
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(identifier, password,
                null);

        return authenticationManager.authenticate(authToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authentication) throws IOException, ServletException {

        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = customUserDetails.getUsername();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();
        Long userId = customUserDetails.getId();
        int tokenVersion = customUserDetails.getUserEntity().getTokenVersion() == null
                ? 0
                : customUserDetails.getUserEntity().getTokenVersion();
        // Access Token 유효 기간 설정
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; // 2시간

        // JWT 생성
        String accessToken = jwtUtil.createJwt(email, role, userId, accessTokenExpiredMs, tokenVersion);
        String refreshToken = jwtUtil.createRefreshToken(email, role, userId, tokenVersion);

        // Refresh Token DB 저장/업데이트
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = resolveIpAddress(request);
        String deviceType = resolveDeviceType(userAgent);
        String deviceLabel = resolveDeviceLabel(userAgent, deviceType);
        String browser = resolveBrowser(userAgent);
        String os = resolveOs(userAgent);
        LocalDateTime now = LocalDateTime.now();

        List<RefreshToken> existingTokens = refreshRepository.findAllByEmailOrderByIdDesc(email);
        RefreshToken matchedToken = existingTokens.stream()
                .filter(item -> isSameSessionContext(item, deviceType, deviceLabel, browser, os, ipAddress))
                .findFirst()
                .orElse(null);

        if (matchedToken == null) {
            matchedToken = new RefreshToken();
            matchedToken.setEmail(email);
            matchedToken.setDeviceType(deviceType);
            matchedToken.setDeviceLabel(deviceLabel);
            matchedToken.setBrowser(browser);
            matchedToken.setOs(os);
            matchedToken.setIp(ipAddress);
        }

        matchedToken.setToken(refreshToken);
        matchedToken.setExpiryDate(now.plusWeeks(1));
        matchedToken.setLastSeenAt(now);
        refreshRepository.save(matchedToken);

        // 쿠키에 Access/Refresh Token 동시 추가
        int accessTokenMaxAge = (int) (accessTokenExpiredMs / 1000);
        addSameSiteCookie(response, "Authorization", accessToken, accessTokenMaxAge);

        int refreshTokenMaxAge = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);
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
                role);

        response.getWriter().write(jsonResponse);
        response.getWriter().flush();

    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failed) throws IOException, ServletException {
        // 인증 실패 시 401 Unauthorized 응답
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\": \"Login Failed\", \"message\": \"" + failed.getMessage() + "\"}");
        response.getWriter().flush();
    }

    private void addSameSiteCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        // HttpOnly: true, Path: / (모든 경로), SameSite: Lax (로컬 개발 환경 호환)
        String cookieString = String.format("%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax",
                name, value, maxAgeSeconds);
        response.addHeader("Set-Cookie", cookieString);
    }

    private boolean isSameSessionContext(RefreshToken token, String deviceType, String deviceLabel, String browser, String os,
            String ipAddress) {
        if (token == null) {
            return false;
        }
        String tokenDeviceType = normalizeText(token.getDeviceType(), "desktop");
        String tokenDeviceLabel = normalizeText(token.getDeviceLabel(), "알 수 없는 기기");
        String tokenBrowser = normalizeText(token.getBrowser(), "Unknown");
        String tokenOs = normalizeText(token.getOs(), "Unknown");
        String tokenIp = normalizeText(token.getIp(), "unknown");

        if (!tokenDeviceType.equals(deviceType)) {
            return false;
        }
        if (!tokenDeviceLabel.equals(deviceLabel)) {
            return false;
        }
        if (!tokenBrowser.equals(browser)) {
            return false;
        }
        if (!tokenOs.equals(os)) {
            return false;
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            return tokenIp == null || "unknown".equals(tokenIp);
        }
        return tokenIp.equals(ipAddress);
    }

    private String normalizeText(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : null;
    }

    private String resolveDeviceType(String userAgent) {
        if (userAgent == null) {
            return "desktop";
        }

        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return "mobile";
        }
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "tablet";
        }

        return "desktop";
    }

    private String resolveDeviceLabel(String userAgent, String deviceType) {
        if (userAgent == null || userAgent.isBlank()) {
            return "알 수 없는 기기";
        }

        String ua = userAgent.toLowerCase();
        if (ua.contains("iphone")) {
            return "iPhone";
        }
        if (ua.contains("ipad")) {
            return "iPad";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows PC";
        }
        if (ua.contains("macintosh") || ua.contains("mac os")) {
            return "Mac";
        }
        if (ua.contains("linux")) {
            return "Linux PC";
        }

        return "desktop".equals(deviceType) ? "데스크톱" : "모바일 기기";
    }

    private String resolveBrowser(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/") || ua.contains("edge/")) {
            return "Microsoft Edge";
        }
        if (ua.contains("chrome/")) {
            return "Chrome";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        if (ua.contains("safari/") && !ua.contains("chrome/")) {
            return "Safari";
        }
        if (ua.contains("opera/") || ua.contains("opr/")) {
            return "Opera";
        }

        return "Unknown";
    }

    private String resolveOs(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "iOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }

        return "Unknown";
    }

}
