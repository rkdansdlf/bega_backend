package com.example.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

        String forwardedFor = extractClientIp(joinForwardedForLines(request));
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor;
        }

        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }

        return remoteAddr;
    }

    /**
     * 프록시가 헤더를 여러 줄로 나눠 보내더라도 홉 순서대로 한 목록으로 합쳐서 해석한다.
     * getHeader 는 첫 줄만 돌려주므로, 그 값만 쓰면 뒤에 덧붙은 신뢰 홉이 무시되고 공격자 값이 채택될 수 있다.
     */
    private String joinForwardedForLines(HttpServletRequest request) {
        List<String> lines = Collections.list(request.getHeaders("X-Forwarded-For"));
        return lines.isEmpty() ? null : String.join(",", lines);
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

    /**
     * 각 홉은 자신이 받은 주소를 오른쪽 끝에 덧붙이므로, 오른쪽에서부터 신뢰 프록시가 아닌 첫 주소가
     * 실제 클라이언트다. 왼쪽 값은 요청자가 임의로 넣을 수 있어 신뢰하지 않는다.
     * 모든 값이 신뢰 프록시면 가장 왼쪽 값을 돌려준다.
     */
    private String extractClientIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }

        String[] values = forwardedFor.split(",");
        String leftmost = null;
        for (int i = values.length - 1; i >= 0; i--) {
            String candidate = normalize(values[i]);
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            leftmost = candidate;
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return leftmost;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
