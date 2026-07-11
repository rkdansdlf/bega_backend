package com.example.auth.service;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.AccountStatusUtil;
import com.example.auth.util.JWTUtil;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.RefreshTokenRevokeFailedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReissueService {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;
    private final AuthSessionService authSessionService;
    private final AuthSecurityMonitoringService authSecurityMonitoringService;
    private final RefreshTokenReuseDetector refreshTokenReuseDetector;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    @Transactional
    public ReissuedTokens reissue(
            String refreshToken,
            HttpServletRequest request,
            Consumer<ReissuedTokens> tokenDelivery) {
        if (refreshToken == null) {
            throw rejectBadRequest("REFRESH_TOKEN_MISSING", "Refresh Token이 없습니다.", request, null, null);
        }

        String tokenType;
        try {
            tokenType = jwtUtil.getTokenType(refreshToken);
        } catch (Exception exception) {
            throw rejectBadRequest(
                    "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.", request, null, null);
        }
        if (!"refresh".equals(tokenType)) {
            throw rejectBadRequest(
                    "INVALID_REFRESH_TOKEN_TYPE", "유효하지 않은 Refresh Token 타입입니다.", request, null, null);
        }

        if (jwtUtil.isExpired(refreshToken)) {
            try {
                List<RefreshToken> expiredTokens = refreshRepository.findAllByToken(refreshToken);
                if (!expiredTokens.isEmpty()) {
                    refreshRepository.deleteAll(expiredTokens);
                }
            } catch (Exception exception) {
                log.warn("Expired refresh token cleanup skipped: {}", exception.getMessage(), exception);
            }
            throw rejectBadRequest(
                    "REFRESH_TOKEN_EXPIRED", "Refresh Token이 만료되었습니다.", request, null, null);
        }

        List<RefreshToken> matchedTokens = refreshRepository.findAllByTokenForUpdate(refreshToken);
        Optional<RefreshToken> matchedToken = matchedTokens.stream().findFirst();
        if (matchedToken.isEmpty()) {
            Optional<Long> reuseUserId = refreshTokenReuseDetector.findReuseUserId(refreshToken);
            if (reuseUserId.isPresent()) {
                Long reusedUserId = reuseUserId.get();
                RefreshTokenRevocationService.RevokedRefreshSessions revokedSessions;
                try {
                    revokedSessions = refreshTokenRevocationService.revokeAllSessionsAfterReuse(reusedUserId);
                } catch (RefreshTokenRevokeFailedException exception) {
                    authSecurityMonitoringService.recordRefreshReissueReject(RefreshTokenRevokeFailedException.CODE);
                    logReissueReject(RefreshTokenRevokeFailedException.CODE, request, null, reusedUserId);
                    throw exception;
                }
                authSecurityMonitoringService.recordRefreshReissueReject("REFRESH_TOKEN_REUSE_DETECTED");
                logReissueReject("REFRESH_TOKEN_REUSE_DETECTED", request, revokedSessions.email(), reusedUserId);
                throw new BadRequestBusinessException(
                        "REFRESH_TOKEN_REUSE_DETECTED",
                        "보안 경고: 재사용이 감지되어 모든 세션이 종료되었습니다. 다시 로그인해주세요.");
            }
            throw rejectBadRequest(
                    "REFRESH_TOKEN_NOT_FOUND", "잘못된 Refresh Token입니다.", request, null, null);
        }

        RefreshToken existingToken = matchedToken.get();
        String email = existingToken.getEmail();
        Long userId = jwtUtil.getUserId(refreshToken);
        if (userId == null) {
            throw rejectBadRequest(
                    "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.", request, email, null);
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (!isAuthoritativeRefreshUser(user, userId, jwtUtil.getTokenVersion(refreshToken))) {
            logReissueReject("INVALID_AUTHOR", request, email, userId);
            throw new InvalidAuthorException("계정 정보가 유효하지 않습니다. 다시 로그인해주세요.");
        }

        long accessTokenExpiredMs = jwtUtil.getAccessTokenExpirationTime();
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String newAccessToken = jwtUtil.createJwt(
                user.getEmail(), user.getRole(), userId, accessTokenExpiredMs, tokenVersion);
        AuthSessionService.IssuedRefreshSession issuedRefreshSession = authSessionService.rotateRefreshSession(
                existingToken,
                user.getEmail(),
                user.getRole(),
                userId,
                tokenVersion,
                request,
                jwtUtil.getSessionId(refreshToken));
        int refreshTokenMaxAge = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);

        ReissuedTokens tokens = new ReissuedTokens(
                newAccessToken,
                accessTokenExpiredMs / 1000,
                issuedRefreshSession.token(),
                refreshTokenMaxAge);
        tokenDelivery.accept(tokens);
        return tokens;
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
