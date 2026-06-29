package com.example.auth.controller;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.example.auth.util.AuthCookieUtil;
import com.example.auth.util.AccountStatusUtil;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.AuthSessionMetadataResolver;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.RefreshTokenReuseDetector;
import com.example.auth.service.RefreshTokenRevocationService;
import com.example.common.config.AllowedOriginResolver;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ForbiddenBusinessException;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.RefreshTokenRevokeFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.transaction.annotation.Transactional;
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
    private final AuthSecurityMonitoringService authSecurityMonitoringService;
    private final RefreshTokenReuseDetector refreshTokenReuseDetector;
    private final RefreshTokenRevocationService refreshTokenRevocationService;
    private final AllowedOriginResolver allowedOriginResolver;

    public ReissueController(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository,
            AuthCookieUtil authCookieUtil, AuthSessionService authSessionService,
            AuthSecurityMonitoringService authSecurityMonitoringService,
            RefreshTokenReuseDetector refreshTokenReuseDetector,
            RefreshTokenRevocationService refreshTokenRevocationService,
            AllowedOriginResolver allowedOriginResolver) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.authCookieUtil = authCookieUtil;
        this.authSessionService = authSessionService;
        this.authSecurityMonitoringService = authSecurityMonitoringService;
        this.refreshTokenReuseDetector = refreshTokenReuseDetector;
        this.refreshTokenRevocationService = refreshTokenRevocationService;
        this.allowedOriginResolver = allowedOriginResolver;
    }

    @PostMapping("/reissue")
    @Transactional
    public ResponseEntity<ApiResponse> reissue(HttpServletRequest request, HttpServletResponse response) {
        validateProvidedOrigin(request);

        // Refresh Token 추출
        String refreshToken = authSessionService.extractRefreshToken(request);

        // Refresh Token이 없으면 권한 없음 처리
        if (refreshToken == null) {
            throw rejectBadRequest("REFRESH_TOKEN_MISSING", "Refresh Token이 없습니다.", request, null, null);
        }

        // [Security Fix] refresh 타입 토큰만 재발급에 사용 허용
        String tokenType;
        try {
            tokenType = jwtUtil.getTokenType(refreshToken);
        } catch (Exception e) {
            throw rejectBadRequest("INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.", request, null, null);
        }
        if (!"refresh".equals(tokenType)) {
            throw rejectBadRequest("INVALID_REFRESH_TOKEN_TYPE", "유효하지 않은 Refresh Token 타입입니다.", request, null, null);
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
            throw rejectBadRequest("REFRESH_TOKEN_EXPIRED", "Refresh Token이 만료되었습니다.", request, null, null);
        }

        List<RefreshToken> matchedTokens = refreshRepository.findAllByTokenForUpdate(refreshToken);
        Optional<RefreshToken> matchedToken = matchedTokens.stream().findFirst();

        if (matchedToken.isEmpty()) {
            // [Security Fix - Medium #4] 재사용 탐지:
            // 이 토큰이 과거에 회전된 적 있으면 탈취 후 재생(replay)으로 간주하고
            // 해당 사용자의 모든 세션을 무효화 + tokenVersion 증가로 기존 Access Token도 강제 폐기.
            Optional<Long> reuseUserId = refreshTokenReuseDetector.findReuseUserId(refreshToken);
            if (reuseUserId.isPresent()) {
                Long reusedUserId = reuseUserId.get();
                RefreshTokenRevocationService.RevokedRefreshSessions revokedSessions;
                try {
                    revokedSessions = refreshTokenRevocationService.revokeAllSessionsAfterReuse(reusedUserId);
                } catch (RefreshTokenRevokeFailedException e) {
                    authSecurityMonitoringService.recordRefreshReissueReject(RefreshTokenRevokeFailedException.CODE);
                    logReissueReject(RefreshTokenRevokeFailedException.CODE, request, null, reusedUserId);
                    throw e;
                }
                authSecurityMonitoringService.recordRefreshReissueReject("REFRESH_TOKEN_REUSE_DETECTED");
                logReissueReject("REFRESH_TOKEN_REUSE_DETECTED", request, revokedSessions.email(), reusedUserId);
                throw new BadRequestBusinessException(
                        "REFRESH_TOKEN_REUSE_DETECTED",
                        "보안 경고: 재사용이 감지되어 모든 세션이 종료되었습니다. 다시 로그인해주세요.");
            }
            // 해당 이메일로 등록된 Refresh Token이 DB에 없으면 띄우기
            throw rejectBadRequest("REFRESH_TOKEN_NOT_FOUND", "잘못된 Refresh Token입니다.", request, null, null);
        }

        RefreshToken existToken = matchedToken.get();
        String email = existToken.getEmail();

        // 새로운 Access Token 및 Refresh Token 생성
        Long userId = jwtUtil.getUserId(refreshToken);
        if (userId == null) {
            throw rejectBadRequest("INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.", request, email, null);
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (!isAuthoritativeRefreshUser(user, userId, jwtUtil.getTokenVersion(refreshToken))) {
            logReissueReject("INVALID_AUTHOR", request, email, userId);
            throw new InvalidAuthorException("계정 정보가 유효하지 않습니다. 다시 로그인해주세요.");
        }

        // Access Token 만료 시간 (2시간)
        long accessTokenExpiredMs = jwtUtil.getAccessTokenExpirationTime();

        // userId와 role의 순서를 교정함 (email, userId, role, expiredMs)
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String newAccessToken = jwtUtil.createJwt(user.getEmail(), user.getRole(), userId, accessTokenExpiredMs, tokenVersion);

        // userId와 role의 순서를 교정함 (email, userId, role)
        AuthSessionService.IssuedRefreshSession issuedRefreshSession = authSessionService.rotateRefreshSession(
                existToken,
                user.getEmail(),
                user.getRole(),
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

    private void validateProvidedOrigin(HttpServletRequest request) {
        String originHeader = request != null ? request.getHeader("Origin") : null;
        String refererHeader = request != null ? request.getHeader("Referer") : null;
        if (isBlank(originHeader) && isBlank(refererHeader)) {
            return;
        }

        String origin = extractOrigin(originHeader);
        String refererOrigin = extractOrigin(refererHeader);
        List<String> allowedOrigins = allowedOriginResolver.resolve();
        if (isAllowedOrigin(origin, allowedOrigins) || isAllowedOrigin(refererOrigin, allowedOrigins)) {
            return;
        }

        authSecurityMonitoringService.recordInvalidOrigin();
        logReissueReject("INVALID_REISSUE_ORIGIN", request, null, null);
        throw new ForbiddenBusinessException(
                "INVALID_REISSUE_ORIGIN",
                "허용되지 않은 출처의 토큰 재발급 요청입니다.");
    }

    private boolean isAllowedOrigin(String origin, List<String> allowedOrigins) {
        if (isBlank(origin)) {
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
        if (isBlank(pattern)) {
            return false;
        }
        if (origin.equals(pattern)) {
            return true;
        }
        if (PatternMatchUtils.simpleMatch(pattern, origin)) {
            return true;
        }

        try {
            URI originUri = URI.create(origin);
            URI patternUri = URI.create(pattern.replace(":*", ""));

            if (pattern.endsWith(":*")) {
                return Objects.equals(originUri.getScheme(), patternUri.getScheme())
                        && Objects.equals(originUri.getHost(), patternUri.getHost());
            }
            return Objects.equals(originUri.getScheme(), patternUri.getScheme())
                    && Objects.equals(originUri.getHost(), patternUri.getHost())
                    && originUri.getPort() == patternUri.getPort();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String extractOrigin(String headerValue) {
        if (isBlank(headerValue)) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BadRequestBusinessException rejectBadRequest(
            String code,
            String message,
            HttpServletRequest request,
            String email,
            Long userId) {
        logReissueReject(code, request, email, userId);
        authSecurityMonitoringService.recordRefreshReissueReject(code);
        return new BadRequestBusinessException(code, message);
    }

    private void logReissueReject(String code, HttpServletRequest request, String email, Long userId) {
        AuthSessionMetadataResolver.SessionMetadata metadata = authSessionService.resolveRequestMetadata(request);
        boolean hasRefreshCookie = authSessionService.extractRefreshToken(request) != null;
        String requestUri = request != null ? request.getRequestURI() : "unknown";
        String origin = request != null ? request.getHeader("Origin") : null;
        log.warn(
                "security_event=REFRESH_REISSUE_REJECT code={} requestUri={} hasRefreshCookie={} email={} userId={} origin={} deviceType={} browser={} os={} ip={}",
                code,
                requestUri,
                hasRefreshCookie,
                email,
                userId,
                origin,
                metadata.deviceType(),
                metadata.browser(),
                metadata.os(),
                metadata.ip());
    }

    private boolean isAuthoritativeRefreshUser(UserEntity user, Long userId, Integer tokenVersionInToken) {
        if (user == null || user.getId() == null || !user.getId().equals(userId)) {
            return false;
        }

        if (!user.isEnabled() || !AccountStatusUtil.isAccountUsable(user)) {
            return false;
        }

        return AccountStatusUtil.hasMatchingTokenVersion(user, tokenVersionInToken);
    }

}
