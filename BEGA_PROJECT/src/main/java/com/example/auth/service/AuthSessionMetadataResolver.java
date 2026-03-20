package com.example.auth.service;

import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthSessionMetadataResolver {

    private final ClientIpResolver clientIpResolver;

    public SessionMetadata resolve(HttpServletRequest request) {
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String ipAddress = clientIpResolver.resolveOrUnknown(request);
        return resolve(userAgent, ipAddress);
    }

    public SessionMetadata resolve(String userAgent, String ipAddress) {
        String deviceType = resolveDeviceType(userAgent);
        return new SessionMetadata(
                deviceType,
                resolveDeviceLabel(userAgent, deviceType),
                resolveBrowser(userAgent),
                resolveOs(userAgent),
                normalizeText(ipAddress, "unknown"),
                LocalDateTime.now());
    }

    public String resolveDeviceType(String userAgent) {
        if (userAgent == null) {
            return "desktop";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "tablet";
        }
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return "mobile";
        }
        return "desktop";
    }

    public String resolveDeviceLabel(String userAgent, String deviceType) {
        if (userAgent == null || userAgent.isBlank()) {
            return defaultDeviceLabel(deviceType);
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone")) {
            return "iPhone";
        }
        if (ua.contains("ipad")) {
            return "iPad";
        }
        if (ua.contains("android")) {
            return "tablet".equals(deviceType) ? "Android 태블릿" : "Android 기기";
        }
        if (ua.contains("windows")) {
            return "Windows PC";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "Mac";
        }
        if (ua.contains("linux")) {
            return "Linux PC";
        }
        return defaultDeviceLabel(deviceType);
    }

    public String resolveBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("edg/") || ua.contains("edge/")) {
            return "Microsoft Edge";
        }
        if (ua.contains("whale/")) {
            return "Whale";
        }
        if (ua.contains("opera/") || ua.contains("opr/")) {
            return "Opera";
        }
        if (ua.contains("chrome/")) {
            return "Chrome";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        if (ua.contains("safari/") && !ua.contains("chrome/")) {
            return "Safari";
        }
        return "Unknown";
    }

    public String resolveOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    public String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String defaultDeviceLabel(String deviceType) {
        return switch (deviceType) {
            case "mobile" -> "모바일 기기";
            case "tablet" -> "태블릿";
            default -> "데스크톱";
        };
    }

    public record SessionMetadata(
            String deviceType,
            String deviceLabel,
            String browser,
            String os,
            String ip,
            LocalDateTime now) {
    }
}
