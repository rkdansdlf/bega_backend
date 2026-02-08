package com.example.auth.controller;

import com.example.auth.dto.OAuth2StateData;
import com.example.auth.service.OAuth2StateService;
import com.example.common.dto.ApiResponse;
import com.example.common.ratelimit.RateLimit;
import com.example.auth.dto.LoginDto;
import com.example.auth.dto.SignupDto;
import com.example.auth.service.UserService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class APIController {

    private final UserService userService;
    private final OAuth2StateService oAuth2StateService;
    private final com.example.auth.service.TokenBlacklistService tokenBlacklistService;

    @org.springframework.beans.factory.annotation.Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    public APIController(UserService userService, OAuth2StateService oAuth2StateService,
            com.example.auth.service.TokenBlacklistService tokenBlacklistService) {
        this.userService = userService;
        this.oAuth2StateService = oAuth2StateService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 일반 회원가입
     */
    @RateLimit(limit = 3, window = 3600) // 1시간에 최대 3회 가입 시도
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signUp(@Valid @RequestBody SignupDto signupDto) {
        userService.signUp(signupDto.toUserDto());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다."));
    }

    /**
     * 로그인
     */
    @RateLimit(limit = 10, window = 60) // 1분에 최대 10회 로그인 시도
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @Valid @RequestBody LoginDto request,
            HttpServletResponse response) {

        // UserService의 인증 로직 호출
        Map<String, Object> loginData = userService.authenticateAndGetToken(
                request.getEmail(),
                request.getPassword());

        String accessToken = (String) loginData.get("accessToken");
        String refreshToken = (String) loginData.get("refreshToken");

        // JWT를 쿠키에 설정 (Access Token)
        Cookie jwtCookie = new Cookie("Authorization", accessToken);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(secureCookie); // Configurable secure flag
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(60 * 60); // 1시간
        response.addCookie(jwtCookie);

        // Refresh Token 쿠키 설정
        Cookie refreshCookie = new Cookie("Refresh", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(secureCookie); // Configurable secure flag
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(60 * 60 * 24 * 7); // 7일
        response.addCookie(refreshCookie);

        // 성공 응답
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", loginData));
    }

    /**
     * 이메일 중복 체크
     */
    @RateLimit(limit = 20, window = 60) // 1분에 최대 20회 이메일 중복 체크
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email.trim().toLowerCase());

        if (exists) {
            return ResponseEntity.ok(ApiResponse.error("이미 사용 중인 이메일입니다."));
        }

        return ResponseEntity.ok(ApiResponse.success("사용 가능한 이메일입니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        // 1. JWT (Authorization) 및 Refresh 쿠키 추출하여 이메일 확인
        String email = null;
        String accessToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("Authorization")) {
                    accessToken = cookie.getValue();
                    try {
                        email = userService.getJWTUtil().getEmail(accessToken);
                    } catch (Exception e) {
                        // 토큰 만료 등의 경우 무시
                    }
                }
            }
        }

        // 2. DB에서 리프레시 토큰 삭제
        if (email != null) {
            userService.deleteRefreshTokenByEmail(email);
        }

        // 3. [Security Fix] Access Token을 블랙리스트에 추가 및 캐시 무효화
        if (accessToken != null) {
            try {
                // 캐시 무효화 (로그아웃된 토큰의 Claims 정보 제거)
                userService.getJWTUtil().evictTokenCache(accessToken);

                java.util.Date expiration = userService.getJWTUtil().getExpiration(accessToken);
                long remainingTime = expiration.getTime() - System.currentTimeMillis();
                if (remainingTime > 0) {
                    tokenBlacklistService.blacklist(accessToken, remainingTime);
                    log.info("Access token blacklisted on logout for email: {}", email);
                }
            } catch (Exception e) {
                log.warn("Failed to blacklist token: {}", e.getMessage());
            }
        }

        // 4. Authorization 쿠키 삭제
        ResponseCookie expireAuthCookie = ResponseCookie.from("Authorization", "")
                .httpOnly(true)
                .secure(secureCookie) // [Security Fix] 환경별 Secure 플래그 설정
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        // 5. Refresh 쿠키 삭제
        ResponseCookie expireRefreshCookie = ResponseCookie.from("Refresh", "")
                .httpOnly(true)
                .secure(secureCookie) // [Security Fix] 환경별 Secure 플래그 설정
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expireAuthCookie.toString())
                .header(HttpHeaders.SET_COOKIE, expireRefreshCookie.toString())
                .body(ApiResponse.success("로그아웃 성공"));
    }

    /**
     * OAuth2 state 조회 및 소비 (one-time use)
     */
    @GetMapping("/oauth2/state/{stateId}")
    public ResponseEntity<?> consumeOAuth2State(@PathVariable String stateId) {
        OAuth2StateData data = oAuth2StateService.consumeState(stateId);
        if (data == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "State not found or already consumed"));
        }
        return ResponseEntity.ok(data);
    }

    /**
     * OAuth2 계정 연동을 위한 Link Token 발급
     * - 인증된 사용자만 호출 가능
     * - 5분 유효의 단기 토큰 반환
     * - 이 토큰을 OAuth2 리다이렉트 URL에 포함하여 연동 모드 활성화
     */
    /**
     * OAuth2 계정 연동을 위한 Link Token 발급
     * - 인증된 사용자만 호출 가능
     * - 5분 유효의 단기 토큰 반환
     * - 이 토큰을 OAuth2 리다이렉트 URL에 포함하여 연동 모드 활성화
     */
    @GetMapping("/link-token")
    public ResponseEntity<?> generateLinkToken() {
        try {
            Long userId = null;
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof Long) {
                userId = (Long) authentication.getPrincipal();
            }

            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("로그인이 필요합니다."));
            }

            // [Security Fix] 5분 유효의 Link Token 생성 (token_type=link explicitly set)
            String linkToken = userService.getJWTUtil().createLinkToken(
                    userId, // userId
                    5 * 60 * 1000L // 5분
            );

            log.info("Link token generated for userId: {}", userId);

            return ResponseEntity.ok(Map.of(
                    "linkToken", linkToken,
                    "expiresIn", 300 // 5분 (초)
            ));

        } catch (Exception e) {
            log.warn("Failed to generate link token: {}", e.getMessage()); // Error -> Warn to avoid noise
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("토큰 생성에 실패했습니다. 유효하지 않은 요청입니다."));
        }
    }
}