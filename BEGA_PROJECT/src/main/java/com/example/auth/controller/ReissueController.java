package com.example.auth.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.auth.util.AuthCookieUtil;
import com.example.auth.service.AuthSessionService;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InvalidAuthorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class ReissueController {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;
    private final AuthCookieUtil authCookieUtil;
    private final AuthSessionService authSessionService;

    public ReissueController(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository,
            AuthCookieUtil authCookieUtil, AuthSessionService authSessionService) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.authCookieUtil = authCookieUtil;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse> reissue(HttpServletRequest request, HttpServletResponse response) {

        // Refresh Token 추출
        String refreshToken = authSessionService.extractRefreshToken(request);

        // Refresh Token이 없으면 권한 없음 처리
        if (refreshToken == null) {
            throw new BadRequestBusinessException("REFRESH_TOKEN_MISSING", "Refresh Token이 없습니다.");
        }

        // [Security Fix] refresh 타입 토큰만 재발급에 사용 허용
        String tokenType;
        try {
            tokenType = jwtUtil.getTokenType(refreshToken);
        } catch (Exception e) {
            throw new BadRequestBusinessException("INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.");
        }
        if (!"refresh".equals(tokenType)) {
            throw new BadRequestBusinessException("INVALID_REFRESH_TOKEN_TYPE", "유효하지 않은 Refresh Token 타입입니다.");
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
            throw new BadRequestBusinessException("REFRESH_TOKEN_EXPIRED", "Refresh Token이 만료되었습니다.");
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
            throw new BadRequestBusinessException("REFRESH_TOKEN_NOT_FOUND", "잘못된 Refresh Token입니다.");
        }

        RefreshToken existToken = matchedToken.get();

        // 새로운 Access Token 및 Refresh Token 생성
        String role = jwtUtil.getRole(refreshToken);

        Long userId = jwtUtil.getUserId(refreshToken);
        if (userId == null) {
            throw new BadRequestBusinessException("INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.");
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (!isAuthoritativeRefreshUser(user, userId, jwtUtil.getTokenVersion(refreshToken))) {
            throw new InvalidAuthorException("계정 정보가 유효하지 않습니다. 다시 로그인해주세요.");
        }

        // Access Token 만료 시간 (2시간)
        long accessTokenExpiredMs = jwtUtil.getAccessTokenExpirationTime();

        // userId와 role의 순서를 교정함 (email, userId, role, expiredMs)
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String newAccessToken = jwtUtil.createJwt(email, role, userId, accessTokenExpiredMs, tokenVersion);

        // userId와 role의 순서를 교정함 (email, userId, role)
        AuthSessionService.IssuedRefreshSession issuedRefreshSession = authSessionService.rotateRefreshSession(
                existToken,
                email,
                role,
                userId,
                tokenVersion,
                request,
                jwtUtil.getSessionId(refreshToken));
        String newRefreshToken = issuedRefreshSession.token();

        // Access Token 쿠키
        ResponseCookie accessCookie = authCookieUtil.buildAuthCookie(newAccessToken, accessTokenExpiredMs / 1000);
        authCookieUtil.addCookieHeader(response, accessCookie);

        // Refresh Token 쿠키
        int refreshTokenMaxAge = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);
        ResponseCookie refreshCookie = authCookieUtil.buildRefreshCookie(newRefreshToken, refreshTokenMaxAge);
        authCookieUtil.addCookieHeader(response, refreshCookie);

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

}
