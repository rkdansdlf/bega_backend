package com.example.auth.service;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionLogoutService {

    private final JWTUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshRepository refreshRepository;
    private final RefreshTokenDigestService refreshTokenDigestService;
    private final RefreshTokenReuseDetector refreshTokenReuseDetector;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    @Transactional
    public void revoke(String refreshToken, Long fallbackAccessUserId) {
        if (refreshToken == null) {
            revokeAllForFallbackUser(fallbackAccessUserId);
            return;
        }
        if (refreshToken.isBlank()) {
            return;
        }

        RefreshTokenReuseDetector.ReuseMarker marker = null;
        Long refreshUserId = resolveSignedRefreshUserId(refreshToken);
        String signedSessionId = resolveSignedRefreshSessionId(refreshToken);
        if (refreshUserId == null) {
            marker = refreshTokenReuseDetector.findReuseMarker(refreshToken).orElse(null);
            if (marker != null) {
                refreshUserId = marker.userId();
            }
        }
        if (refreshUserId == null) {
            return;
        }

        UserEntity lockedUser = userRepository.findByIdForWrite(refreshUserId).orElse(null);
        if (lockedUser == null || lockedUser.getId() == null || !refreshUserId.equals(lockedUser.getId())
                || lockedUser.getEmail() == null || lockedUser.getEmail().isBlank()) {
            return;
        }

        String tokenDigest = refreshTokenDigestService.digest(refreshToken);
        List<RefreshToken> matchedTokens = refreshRepository.findAllByTokenDigestForUpdate(tokenDigest);
        if (!matchedTokens.isEmpty()) {
            boolean belongsToLockedUser = matchedTokens.stream()
                    .allMatch(token -> lockedUser.getEmail().equalsIgnoreCase(token.getEmail()));
            if (!belongsToLockedUser) {
                throw new IllegalStateException("Refresh token owner does not match its signed user");
            }
            refreshRepository.deleteAll(matchedTokens);
            return;
        }

        if (marker == null) {
            marker = refreshTokenReuseDetector.findReuseMarker(refreshToken).orElse(null);
        }
        if (marker != null && !refreshUserId.equals(marker.userId())) {
            throw new IllegalStateException("Rotated refresh token has no safe session mapping");
        }

        String mappedSessionId = marker != null && marker.sessionId() != null && !marker.sessionId().isBlank()
                ? marker.sessionId()
                : signedSessionId;
        if (mappedSessionId == null || mappedSessionId.isBlank()) {
            throw new IllegalStateException("Rotated refresh token has no safe session mapping");
        }
        if (signedSessionId != null && marker != null && marker.sessionId() != null
                && !marker.sessionId().isBlank() && !signedSessionId.equals(marker.sessionId())) {
            throw new IllegalStateException("Rotated refresh token session mapping does not match its signed session");
        }

        refreshTokenRevocationService.revokeSessionAfterReuse(refreshUserId, mappedSessionId);
    }

    private Long resolveSignedRefreshUserId(String refreshToken) {
        try {
            String tokenType = jwtUtil.getTokenType(refreshToken);
            if (tokenType == null || !"refresh".equals(tokenType.trim().toLowerCase(Locale.ROOT))) {
                return null;
            }
            return jwtUtil.getUserId(refreshToken);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String resolveSignedRefreshSessionId(String refreshToken) {
        try {
            String tokenType = jwtUtil.getTokenType(refreshToken);
            if (tokenType == null || !"refresh".equals(tokenType.trim().toLowerCase(Locale.ROOT))) {
                return null;
            }
            return jwtUtil.getSessionId(refreshToken);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void revokeAllForFallbackUser(Long userId) {
        if (userId == null) {
            return;
        }
        userRepository.findByIdForWrite(userId)
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .ifPresent(user -> refreshRepository.deleteByEmail(user.getEmail()));
    }
}
