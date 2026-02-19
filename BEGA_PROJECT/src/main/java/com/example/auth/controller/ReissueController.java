package com.example.auth.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.extern.slf4j.Slf4j;

import com.example.common.dto.ApiResponse;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import com.example.auth.repository.RefreshRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class ReissueController {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;

    public ReissueController(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

        // Refresh Token 추출
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("Refresh")) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        // Refresh Token이 없으면 권한 없음 처리
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Refresh Token이 없습니다."));
        }

        // [Security Fix] refresh 타입 토큰만 재발급에 사용 허용
        String tokenType;
        try {
            tokenType = jwtUtil.getTokenType(refreshToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("유효하지 않은 Refresh Token입니다."));
        }
        if (!"refresh".equals(tokenType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("유효하지 않은 Refresh Token 타입입니다."));
        }

        // Refresh Token 만료 확인
        if (jwtUtil.isExpired(refreshToken)) {
            try {
                // 만료된 토큰은 토큰 값 기준으로만 정리 (멀티세션 환경에서 타 세션을 건드리지 않음)
                List<RefreshToken> expiredTokens = refreshRepository.findAllByToken(refreshToken);
                if (!expiredTokens.isEmpty()) {
                    refreshRepository.deleteAll(expiredTokens);
                }
            } catch (Exception e) {
                log.warn("Expired refresh token cleanup skipped: {}", e.getMessage(), e);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Refresh Token이 만료되었습니다."));
        }

        // DB에서 Refresh Token 확인

        // 토큰에서 이메일 가져오기
        String email = jwtUtil.getEmail(refreshToken);

        // 가져온 이메일을 통해 DB에서 RefreshToken 엔티티 찾기
        List<RefreshToken> existTokens = refreshRepository.findAllByEmailOrderByIdDesc(email);
        final String finalRefreshToken = refreshToken;
        Optional<RefreshToken> matchedToken = existTokens.stream()
                .filter(item -> finalRefreshToken.equals(item.getToken()))
                .findFirst();

        if (matchedToken.isEmpty()) {
            // 해당 이메일로 등록된 Refresh Token이 DB에 없으면 띄우기
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("잘못된 Refresh Token입니다."));
        }

        RefreshToken existToken = matchedToken.get();

        // 새로운 Access Token 및 Refresh Token 생성
        String role = jwtUtil.getRole(refreshToken);

        Long userId = jwtUtil.getUserId(refreshToken);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("유효하지 않은 Refresh Token입니다."));
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (!isAuthoritativeRefreshUser(user, userId, jwtUtil.getTokenVersion(refreshToken))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(buildInvalidAuthorErrorResponse("계정 정보가 유효하지 않습니다. 다시 로그인해주세요."));
        }

        // Access Token 만료 시간 (2시간)
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L;

        // userId와 role의 순서를 교정함 (email, userId, role, expiredMs)
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String newAccessToken = jwtUtil.createJwt(email, role, userId, accessTokenExpiredMs, tokenVersion);

        // userId와 role의 순서를 교정함 (email, userId, role)
        String newRefreshToken = jwtUtil.createRefreshToken(email, role, userId, tokenVersion);

        // DB 정보 저장
        existToken.setToken(newRefreshToken);
        existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
        existToken.setLastSeenAt(LocalDateTime.now());
        String userAgent = request.getHeader("User-Agent");
        String deviceType = resolveDeviceType(userAgent);
        existToken.setDeviceType(deviceType);
        existToken.setDeviceLabel(resolveDeviceLabel(userAgent, deviceType));
        existToken.setBrowser(resolveBrowser(userAgent));
        existToken.setOs(resolveOs(userAgent));
        existToken.setIp(resolveIpAddress(request));
        refreshRepository.save(existToken);

        // Access Token 쿠키
        response.addCookie(createCookie("Authorization", newAccessToken, (int) (accessTokenExpiredMs / 1000)));

        // Refresh Token 쿠키
        int refreshTokenMaxAge = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);
        response.addCookie(createCookie("Refresh", newRefreshToken, refreshTokenMaxAge));

        return ResponseEntity.ok(ApiResponse.success("토큰이 성공적으로 재발급되었습니다."));
    }

    private boolean isAuthoritativeRefreshUser(UserEntity user, Long userId, Integer tokenVersionInToken) {
        if (user == null || user.getId() == null || !user.getId().equals(userId)) {
            return false;
        }

        if (!user.isEnabled() || !isAccountUsable(user)) {
            return false;
        }

        int currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersionInToken == null) {
            return currentTokenVersion == 0;
        }

        return currentTokenVersion == tokenVersionInToken;
    }

    private boolean isAccountUsable(UserEntity user) {
        if (!user.isLocked()) {
            return true;
        }

        if (user.getLockExpiresAt() == null) {
            return false;
        }

        return user.getLockExpiresAt().isBefore(LocalDateTime.now());
    }

    private Map<String, Object> buildInvalidAuthorErrorResponse(String message) {
        return Map.of(
                "success", false,
                "code", "INVALID_AUTHOR",
                "message", message);
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
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
        if (ua.contains("safari/") && !ua.contains("chrome")) {
            return "Safari";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }

        return "Unknown";
    }

    private String resolveOs(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase();
        if (ua.contains("iphone")) {
            return "iOS";
        }
        if (ua.contains("ipad")) {
            return "iPadOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("macintosh") || ua.contains("mac os")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }

        return "Unknown";
    }

    // 쿠키 생성 헬퍼 메서드
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 로컬 개발 환경(HTTP)에서는 false로 설정

        return cookie;
    }
}
