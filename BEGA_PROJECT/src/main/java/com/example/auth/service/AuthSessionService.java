package com.example.auth.service;

import com.example.auth.entity.RefreshToken;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final RefreshRepository refreshRepository;
    private final JWTUtil jwtUtil;
    private final AuthSessionMetadataResolver authSessionMetadataResolver;

    public record PreparedRefreshSession(
            String sessionId,
            RefreshToken matchedToken,
            AuthSessionMetadataResolver.SessionMetadata metadata) {
    }

    public record IssuedRefreshSession(String token, String sessionId) {
    }

    @Transactional(readOnly = true)
    public PreparedRefreshSession prepareRefreshSession(String email, HttpServletRequest request) {
        List<RefreshToken> tokens = findRefreshTokensByEmail(email);
        AuthSessionMetadataResolver.SessionMetadata metadata = authSessionMetadataResolver.resolve(request);
        String currentRefreshToken = extractRefreshToken(request);

        RefreshToken matchedToken = null;
        if (currentRefreshToken != null) {
            matchedToken = tokens.stream()
                    .filter(token -> token.getToken() != null)
                    .filter(token -> currentRefreshToken.equals(token.getToken()))
                    .findFirst()
                    .orElse(null);

            if (matchedToken == null) {
                String requestedSessionId = safelyExtractSessionId(currentRefreshToken);
                if (requestedSessionId != null) {
                    matchedToken = tokens.stream()
                            .filter(token -> requestedSessionId.equals(normalizeSessionId(token.getSessionId())))
                            .findFirst()
                            .orElse(null);
                }
            }
        }

        if (matchedToken == null) {
            matchedToken = tokens.stream()
                .filter(token -> isSameSessionContext(token, metadata))
                .findFirst()
                .orElse(null);
        }

        String sessionId = normalizeSessionId(matchedToken != null ? matchedToken.getSessionId() : null);
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        return new PreparedRefreshSession(sessionId, matchedToken, metadata);
    }

    @Transactional
    public IssuedRefreshSession issueRefreshSession(
            String email,
            String role,
            Long userId,
            Integer tokenVersion,
            HttpServletRequest request) {
        PreparedRefreshSession prepared = prepareRefreshSession(email, request);
        String refreshToken = jwtUtil.createRefreshToken(email, role, userId, tokenVersion, prepared.sessionId());
        persistRefreshToken(email, prepared.sessionId(), refreshToken, prepared.matchedToken(), prepared.metadata());
        return new IssuedRefreshSession(refreshToken, prepared.sessionId());
    }

    @Transactional
    public String issueRefreshToken(
            String email,
            String role,
            Long userId,
            Integer tokenVersion,
            HttpServletRequest request) {
        return issueRefreshSession(email, role, userId, tokenVersion, request).token();
    }

    @Transactional
    public IssuedRefreshSession rotateRefreshSession(
            RefreshToken existingToken,
            String email,
            String role,
            Long userId,
            Integer tokenVersion,
            HttpServletRequest request,
            String requestedSessionId) {
        AuthSessionMetadataResolver.SessionMetadata metadata = authSessionMetadataResolver.resolve(request);
        String sessionId = normalizeSessionId(requestedSessionId);
        if (sessionId == null && existingToken != null) {
            sessionId = normalizeSessionId(existingToken.getSessionId());
        }
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        String refreshToken = jwtUtil.createRefreshToken(email, role, userId, tokenVersion, sessionId);
        persistRefreshToken(email, sessionId, refreshToken, existingToken, metadata);
        return new IssuedRefreshSession(refreshToken, sessionId);
    }

    @Transactional
    public String rotateRefreshToken(
            RefreshToken existingToken,
            String email,
            String role,
            Long userId,
            Integer tokenVersion,
            HttpServletRequest request) {
        String requestedSessionId = existingToken == null ? null : existingToken.getSessionId();
        return rotateRefreshSession(
                existingToken,
                email,
                role,
                userId,
                tokenVersion,
                request,
                requestedSessionId).token();
    }

    @Transactional
    public void deleteRefreshTokenByEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        refreshRepository.deleteByEmail(email);
    }

    @Transactional
    public void deleteRefreshToken(RefreshToken refreshToken) {
        if (refreshToken == null) {
            return;
        }
        refreshRepository.delete(refreshToken);
    }

    @Transactional
    public void deleteRefreshTokens(Iterable<RefreshToken> refreshTokens) {
        if (refreshTokens == null) {
            return;
        }
        refreshRepository.deleteAll(refreshTokens);
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> findRefreshTokensByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return refreshRepository.findAllByEmailOrderByIdDesc(email);
    }

    @Transactional(readOnly = true)
    public RefreshToken resolveCurrentSessionToken(List<RefreshToken> refreshTokens, HttpServletRequest request) {
        if (refreshTokens == null || refreshTokens.isEmpty()) {
            return null;
        }

        String currentRefreshToken = extractRefreshToken(request);
        if (currentRefreshToken != null) {
            String requestedSessionId = safelyExtractSessionId(currentRefreshToken);
            if (requestedSessionId != null) {
                RefreshToken sessionIdMatchedToken = refreshTokens.stream()
                        .filter(token -> !isRefreshTokenExpired(token))
                        .filter(token -> requestedSessionId.equals(normalizeSessionId(token.getSessionId())))
                        .findFirst()
                        .orElse(null);
                if (sessionIdMatchedToken != null) {
                    return sessionIdMatchedToken;
                }
            }

            RefreshToken cookieMatchedToken = refreshTokens.stream()
                    .filter(token -> token.getToken() != null)
                    .filter(token -> currentRefreshToken.equals(token.getToken()))
                    .filter(token -> !isRefreshTokenExpired(token))
                    .findFirst()
                    .orElse(null);
            if (cookieMatchedToken != null) {
                return cookieMatchedToken;
            }
        }

        AuthSessionMetadataResolver.SessionMetadata metadata = authSessionMetadataResolver.resolve(request);
        return refreshTokens.stream()
                .filter(token -> !isRefreshTokenExpired(token))
                .filter(token -> isSameSessionContext(token, metadata))
                .findFirst()
                .orElse(null);
    }

    public AuthSessionMetadataResolver.SessionMetadata resolveRequestMetadata(HttpServletRequest request) {
        return authSessionMetadataResolver.resolve(request);
    }

    public String normalizeText(String value, String fallback) {
        return authSessionMetadataResolver.normalizeText(value, fallback);
    }

    public String extractCookieValue(HttpServletRequest request, String cookieName) {
        if (!"Refresh".equals(cookieName)) {
            if (request == null) {
                return null;
            }
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            for (Cookie cookie : cookies) {
                if (cookieName != null && cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
            return null;
        }
        return extractRefreshToken(request);
    }

    public String extractRefreshToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("Refresh".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    public boolean isRefreshTokenExpired(RefreshToken refreshToken) {
        return refreshToken == null
                || refreshToken.getExpiryDate() == null
                || refreshToken.getExpiryDate().isBefore(LocalDateTime.now());
    }

    public String resolveSessionIdentifier(RefreshToken refreshToken) {
        if (refreshToken == null) {
            return null;
        }

        String sessionId = normalizeSessionId(refreshToken.getSessionId());
        if (sessionId != null) {
            return sessionId;
        }
        if (refreshToken.getId() != null) {
            return String.valueOf(refreshToken.getId());
        }
        return String.valueOf(Math.abs(Objects.hash(refreshToken.getEmail(), refreshToken.getToken())));
    }

    private RefreshToken persistRefreshToken(
            String email,
            String sessionId,
            String token,
            RefreshToken existingToken,
            AuthSessionMetadataResolver.SessionMetadata metadata) {
        RefreshToken target = existingToken != null ? existingToken : new RefreshToken();
        target.setEmail(email);
        target.setSessionId(sessionId);
        target.setToken(token);
        target.setExpiryDate(metadata.now().plusSeconds(Math.max(1L, jwtUtil.getRefreshTokenExpirationTime() / 1000)));
        target.setDeviceType(metadata.deviceType());
        target.setDeviceLabel(metadata.deviceLabel());
        target.setBrowser(metadata.browser());
        target.setOs(metadata.os());
        target.setIp(metadata.ip());
        target.setLastSeenAt(metadata.now());
        return refreshRepository.save(target);
    }

    private boolean isSameSessionContext(
            RefreshToken token,
            AuthSessionMetadataResolver.SessionMetadata metadata) {
        if (token == null || metadata == null) {
            return false;
        }

        if (!authSessionMetadataResolver.normalizeText(token.getDeviceType(), "desktop")
                .equals(metadata.deviceType())) {
            return false;
        }
        if (!authSessionMetadataResolver.normalizeText(token.getDeviceLabel(), "알 수 없는 기기")
                .equals(metadata.deviceLabel())) {
            return false;
        }
        if (!authSessionMetadataResolver.normalizeText(token.getBrowser(), "Unknown")
                .equals(metadata.browser())) {
            return false;
        }
        if (!authSessionMetadataResolver.normalizeText(token.getOs(), "Unknown")
                .equals(metadata.os())) {
            return false;
        }

        String tokenIp = authSessionMetadataResolver.normalizeText(token.getIp(), "unknown");
        if (metadata.ip() == null || metadata.ip().isBlank()) {
            return "unknown".equals(tokenIp);
        }
        return tokenIp.equals(metadata.ip());
    }

    private String safelyExtractSessionId(String refreshToken) {
        try {
            return normalizeSessionId(jwtUtil.getSessionId(refreshToken));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionId.trim();
    }
}
