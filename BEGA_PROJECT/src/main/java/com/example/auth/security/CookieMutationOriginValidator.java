package com.example.auth.security;

import com.example.common.config.AllowedOriginResolver;
import com.example.common.exception.ForbiddenBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CookieMutationOriginValidator {

    private final AllowedOriginResolver allowedOriginResolver;

    public void validate(HttpServletRequest request, String errorCode) {
        String origin = extractOrigin(request != null ? request.getHeader("Origin") : null);
        String refererOrigin = extractOrigin(request != null ? request.getHeader("Referer") : null);
        List<String> allowedOrigins = allowedOriginResolver.resolve();
        if (isAllowed(origin, allowedOrigins) || isAllowed(refererOrigin, allowedOrigins)) {
            return;
        }
        throw new ForbiddenBusinessException(errorCode, "허용되지 않은 출처의 쿠키 인증 요청입니다.");
    }

    private boolean isAllowed(String origin, List<String> allowedOrigins) {
        if (!StringUtils.hasText(origin) || allowedOrigins == null) {
            return false;
        }
        for (String allowed : allowedOrigins) {
            if (!StringUtils.hasText(allowed)) {
                continue;
            }
            if (origin.equals(allowed) || PatternMatchUtils.simpleMatch(allowed, origin)) {
                return true;
            }
            try {
                URI originUri = URI.create(origin);
                URI allowedUri = URI.create(allowed.replace(":*", ""));
                if (Objects.equals(originUri.getScheme(), allowedUri.getScheme())
                        && Objects.equals(originUri.getHost(), allowedUri.getHost())
                        && (allowed.endsWith(":*") || originUri.getPort() == allowedUri.getPort())) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid origins are rejected.
            }
        }
        return false;
    }

    private String extractOrigin(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        try {
            URI uri = URI.create(headerValue);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            return uri.getPort() < 0
                    ? "%s://%s".formatted(uri.getScheme(), uri.getHost())
                    : "%s://%s:%d".formatted(uri.getScheme(), uri.getHost(), uri.getPort());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
