package com.example.common.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AllowedOriginResolver {

    private static final Logger log = LoggerFactory.getLogger(AllowedOriginResolver.class);

    static final List<String> DEV_LOCAL_DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost",
            "http://localhost:3000",
            "http://localhost:4173",
            "http://localhost:5173",
            "http://localhost:5176",
            "http://localhost:5177",
            "http://localhost:8080",
            "http://host.docker.internal",
            "http://host.docker.internal:3000",
            "http://host.docker.internal:4173",
            "http://host.docker.internal:5173",
            "http://host.docker.internal:5176",
            "http://host.docker.internal:5177",
            "http://host.docker.internal:8080",
            "http://127.0.0.1",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:4173",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5176",
            "http://127.0.0.1:5177",
            "http://127.0.0.1:8080",
            "http://[::1]");

    static final List<String> PROD_ALLOWED_ORIGINS = List.of(
            "https://www.begabaseball.xyz",
            "https://begabaseball.xyz");

    private final Environment environment;
    private final String configuredAllowedOrigins;

    public AllowedOriginResolver(
            Environment environment,
            @Value("${app.allowed-origins:}") String configuredAllowedOrigins) {
        this.environment = environment;
        this.configuredAllowedOrigins = configuredAllowedOrigins;
    }

    public List<String> resolve() {
        List<String> configuredOrigins = parseOrigins(configuredAllowedOrigins);
        log.info("AllowedOriginResolver: raw='{}', parsed={}, isProd={}, isDev={}",
                configuredAllowedOrigins, configuredOrigins, isProdProfile(), isDevOrLocalProfile());

        if (isProdProfile()) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(PROD_ALLOWED_ORIGINS);
            merged.addAll(configuredOrigins);
            List<String> result = List.copyOf(merged);
            log.info("AllowedOriginResolver: prod resolved origins={}", result);
            return result;
        }

        if (isDevOrLocalProfile()) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(DEV_LOCAL_DEFAULT_ALLOWED_ORIGINS);
            merged.addAll(configuredOrigins);
            return List.copyOf(merged);
        }

        if (!configuredOrigins.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(configuredOrigins));
        }

        return DEV_LOCAL_DEFAULT_ALLOWED_ORIGINS;
    }

    boolean isDevOrLocalProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile));
    }

    boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    static List<String> parseOrigins(String rawOrigins) {
        return Arrays.stream(rawOrigins == null ? new String[0] : rawOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> !origin.equals("*"))
                .toList();
    }
}
