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
    private final RefreshTokenDigestService refreshTokenDigestService;

    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public ReissuedTokens reissue(
            String refreshToken,
            HttpServletRequest request,
            Consumer<ReissuedTokens> tokenDelivery) {
        if (refreshToken == null) {
            throw rejectBadRequest("REFRESH_TOKEN_MISSING", "Refresh Token이 없습니다.", request, null, null);
        }

        String tokenType;
        Long userId;
        try {
            tokenType = jwtUtil.getTokenType(refreshToken);
            userId = jwtUtil.getUserId(refreshToken);
        } catch (Exception exception) {
            throw rejectBadRequest(
                    "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.", request, null, null);
        }
        if (!"refresh".equals(tokenType)) {
            throw rejectBadRequest(
                    "INVALID_REFRESH_TOKEN_TYPE", "유효하지 않은 Refresh Token 타입입니다.", request, null, null);
        }

        if (userId == null) {
            throw rejectBadRequest(
                    "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다.", request, null, null);
        }

        UserEntity user = userRepository.findByIdForWrite(userId).orElse(null);
        Integer tokenVersion = jwtUtil.getTokenVersion(refreshToken);
        if (!isAuthoritativeRefreshUser(user, userId, tokenVersion)) {
            logReissueReject("INVALID_AUTHOR", request, null, userId);
            throw new InvalidAuthorException("계정 정보가 유효하지 않습니다. 다시 로그인해주세요.");
        }

        if (jwtUtil.isExpired(refreshToken)) {
            try {
                List<RefreshToken> expiredTokens = refreshRepository.findAllByTokenDigest(
                        refreshTokenDigestService.digest(refreshToken));
                if (!expiredTokens.isEmpty()) {
                    refreshRepository.deleteAll(expiredTokens);
                }
            } catch (Exception exception) {
                log.warn("Expired refresh token cleanup skipped: {}", exception.getMessage(), exception);
            }
            throw rejectBadRequest(
                    "REFRESH_TOKEN_EXPIRED", "Refresh Token이 만료되었습니다.", request, null, null);
        }

        String tokenDigest = refreshTokenDigestService.digest(refreshToken);
        List<RefreshToken> matchedTokens = refreshRepository.findAllByTokenDigestForUpdate(tokenDigest);
        Optional<RefreshToken> matchedToken = matchedTokens.stream().findFirst();
        if (matchedToken.isEmpty()) {
            Optional<RefreshTokenReuseDetector.ReuseMarker> reuseMarker =
                    refreshTokenReuseDetector.findReuseMarker(refreshToken);
            RefreshTokenReuseDetector.ReuseMarker marker = reuseMarker.orElse(null);
            if (marker != null) {
                if (marker.userId() == null || !userId.equals(marker.userId())) {
                    authSecurityMonitoringService.recordRefreshReissueReject(
                            RefreshTokenRevokeFailedException.CODE);
                    logReissueReject(RefreshTokenRevokeFailedException.CODE, request, null, userId);
                    throw new RefreshTokenRevokeFailedException();
                }

            }

            String signedSessionId = jwtUtil.getSessionId(refreshToken);
            String mappedSessionId = marker != null ? marker.sessionId() : null;
            String sessionId = mappedSessionId != null && !mappedSessionId.isBlank()
                    ? mappedSessionId
                    : signedSessionId;
            if (sessionId != null && !sessionId.isBlank()) {
                if (mappedSessionId != null && !mappedSessionId.isBlank()
                        && signedSessionId != null && !signedSessionId.isBlank()
                        && !signedSessionId.equals(mappedSessionId)) {
                    authSecurityMonitoringService.recordRefreshReissueReject(
                            RefreshTokenRevokeFailedException.CODE);
                    logReissueReject(RefreshTokenRevokeFailedException.CODE, request, null, userId);
                    throw new RefreshTokenRevokeFailedException();
                }
                RefreshTokenRevocationService.RevokedRefreshSessions revokedSessions;
                try {
                    revokedSessions = refreshTokenRevocationService.revokeSessionAfterReuse(userId, sessionId);
                } catch (RefreshTokenRevokeFailedException exception) {
                    authSecurityMonitoringService.recordRefreshReissueReject(RefreshTokenRevokeFailedException.CODE);
                    logReissueReject(RefreshTokenRevokeFailedException.CODE, request, null, userId);
                    throw exception;
                }
                authSecurityMonitoringService.recordRefreshReissueReject("REFRESH_TOKEN_REUSE_DETECTED");
                logReissueReject("REFRESH_TOKEN_REUSE_DETECTED", request, revokedSessions.email(), userId);
                throw new RefreshTokenReuseDetectedException();
            }
            if (marker != null) {
                authSecurityMonitoringService.recordRefreshReissueReject(
                        RefreshTokenRevokeFailedException.CODE);
                logReissueReject(RefreshTokenRevokeFailedException.CODE, request, null, userId);
                throw new RefreshTokenRevokeFailedException();
            }
            throw rejectBadRequest(
                    "REFRESH_TOKEN_NOT_FOUND", "잘못된 Refresh Token입니다.", request, null, userId);
        }

        boolean belongsToLockedUser = matchedTokens.stream()
                .allMatch(token -> user.getEmail().equalsIgnoreCase(token.getEmail()));
        if (!belongsToLockedUser) {
            logReissueReject("INVALID_AUTHOR", request, null, userId);
            throw new InvalidAuthorException("계정 정보가 유효하지 않습니다. 다시 로그인해주세요.");
        }

        RefreshToken existingToken = matchedToken.get();
        long accessTokenExpiredMs = jwtUtil.getAccessTokenExpirationTime();
        int currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String newAccessToken = jwtUtil.createJwt(
                user.getEmail(), user.getRole(), userId, accessTokenExpiredMs, currentTokenVersion);
        AuthSessionService.IssuedRefreshSession issuedRefreshSession = authSessionService.rotateRefreshSession(
                existingToken,
                user.getEmail(),
                user.getRole(),
                userId,
                currentTokenVersion,
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

    private static final class RefreshTokenReuseDetectedException extends BadRequestBusinessException {
        private RefreshTokenReuseDetectedException() {
            super(
                    "REFRESH_TOKEN_REUSE_DETECTED",
                    "보안 경고: 재사용이 감지되어 해당 세션만 종료했습니다. 다시 로그인해주세요.");
        }
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

    private void logReissueReject(String code, HttpServletRequest request, String ignoredEmail, Long userId) {
        AuthSessionMetadataResolver.SessionMetadata metadata = authSessionService.resolveRequestMetadata(request);
        boolean hasRefreshCookie = authSessionService.extractRefreshToken(request) != null;
        String requestUri = request != null ? request.getRequestURI() : "unknown";
        String origin = request != null ? request.getHeader("Origin") : null;
        log.warn(
                "security_event=REFRESH_REISSUE_REJECT code={} requestUri={} hasRefreshCookie={} email=[REDACTED] userId={} origin={} deviceType={} browser={} os={} ip={}",
                code,
                requestUri,
                hasRefreshCookie,
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
