package com.example.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1");

    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @Value("${app.trusted-proxies:}") String trustedProxiesProperty,
            Environment environment) {
        LinkedHashSet<String> resolvedTrustedProxies = new LinkedHashSet<>();
        Arrays.stream(trustedProxiesProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(resolvedTrustedProxies::add);

        boolean devOrLocal = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile));
        if (devOrLocal) {
            resolvedTrustedProxies.addAll(LOOPBACK_ADDRESSES);
        }

        this.trustedProxies = Set.copyOf(resolvedTrustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String remoteAddr = normalize(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = extractFirstIp(request.getHeader("X-Forwarded-For"));
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor;
        }

        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }

        return remoteAddr;
    }

    public String resolveOrUnknown(HttpServletRequest request) {
        String resolved = resolve(request);
        return StringUtils.hasText(resolved) ? resolved : "unknown";
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return StringUtils.hasText(remoteAddr) && trustedProxies.contains(remoteAddr);
    }

    private String extractFirstIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }

        String[] values = forwardedFor.split(",");
        if (values.length == 0) {
            return null;
        }

        return normalize(values[0]);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
