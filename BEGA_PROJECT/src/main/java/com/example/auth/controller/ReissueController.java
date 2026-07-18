package com.example.auth.controller;

import com.example.auth.service.AuthSessionMetadataResolver;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.ReissueService;
import com.example.auth.service.ReissuedTokens;
import com.example.auth.util.AuthCookieUtil;
import com.example.common.config.AllowedOriginResolver;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.ForbiddenBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ReissueController {

    private final AuthCookieUtil authCookieUtil;
    private final AuthSessionService authSessionService;
    private final AuthSecurityMonitoringService authSecurityMonitoringService;
    private final AllowedOriginResolver allowedOriginResolver;
    private final ReissueService reissueService;

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<Void>> reissue(HttpServletRequest request, HttpServletResponse response) {
        validateProvidedOrigin(request);

        String refreshToken = authSessionService.extractRefreshToken(request);
        reissueService.reissue(refreshToken, request, tokens -> addTokenCookies(response, tokens));

        return ResponseEntity.ok(ApiResponse.success("토큰이 성공적으로 재발급되었습니다."));
    }

    private void addTokenCookies(HttpServletResponse response, ReissuedTokens tokens) {
        ResponseCookie accessCookie = authCookieUtil.buildAuthCookie(
                tokens.accessToken(), tokens.accessTokenMaxAgeSeconds());
        authCookieUtil.addCookieHeader(response, accessCookie);

        ResponseCookie refreshCookie = authCookieUtil.buildRefreshCookie(
                tokens.refreshToken(), tokens.refreshTokenMaxAgeSeconds());
        authCookieUtil.addCookieHeader(response, refreshCookie);
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
        } catch (IllegalArgumentException exception) {
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
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
}
