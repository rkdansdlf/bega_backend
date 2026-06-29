package com.example.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1");

    /** 정확 일치(단일 IP) 신뢰 프록시. */
    private final Set<String> trustedProxies;
    /** CIDR(예: 172.16.0.0/12) 신뢰 프록시 범위. 동적 도커 브리지 IP 대응용. */
    private final List<IpAddressMatcher> trustedProxyMatchers;

    public ClientIpResolver(
            @Value("${app.trusted-proxies:}") String trustedProxiesProperty,
            Environment environment) {
        LinkedHashSet<String> resolvedTrustedProxies = new LinkedHashSet<>();
        List<IpAddressMatcher> resolvedMatchers = new ArrayList<>();
        Arrays.stream(trustedProxiesProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(entry -> {
                    if (entry.contains("/")) {
                        try {
                            resolvedMatchers.add(new IpAddressMatcher(entry));
                        } catch (IllegalArgumentException ignored) {
                            // Invalid trusted proxy entries are ignored to avoid startup failures.
                        }
                    } else {
                        resolvedTrustedProxies.add(entry);
                    }
                });

        boolean devOrLocal = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile));
        if (devOrLocal) {
            resolvedTrustedProxies.addAll(LOOPBACK_ADDRESSES);
        }

        this.trustedProxies = Set.copyOf(resolvedTrustedProxies);
        this.trustedProxyMatchers = List.copyOf(resolvedMatchers);
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
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        if (trustedProxies.contains(remoteAddr)) {
            return true;
        }
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(remoteAddr)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // IP version mismatches are not trusted.
            }
        }
        return false;
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
