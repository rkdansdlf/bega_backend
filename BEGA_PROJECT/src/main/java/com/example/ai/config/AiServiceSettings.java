package com.example.ai.config;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiServiceSettings {

    static final String LOCAL_DEV_AI_SERVICE_URL = "http://localhost:8001";
    static final String LOCAL_DEV_AI_INTERNAL_TOKEN = "local-dev-ai-internal-token";
    private static final List<Path> LOCAL_TOKEN_ENV_PATHS = List.of(
            Path.of("bega_AI", ".env"),
            Path.of("bega_AI", ".env.prod"),
            Path.of(".env.prod"),
            Path.of(".env"));

    private final Environment environment;
    private final String rawServiceUrl;
    private final String rawInternalToken;
    private final Path workspaceRoot;
    private final boolean allowLoopbackServiceUrlInProd;

    @Autowired
    public AiServiceSettings(
            Environment environment,
            @org.springframework.beans.factory.annotation.Value("${ai.service-url:}") String rawServiceUrl,
            @org.springframework.beans.factory.annotation.Value("${ai.internal-token:}") String rawInternalToken,
            @org.springframework.beans.factory.annotation.Value("${app.ai.proxy.allow-loopback-service-url-in-prod:false}") boolean allowLoopbackServiceUrlInProd) {
        this(environment, rawServiceUrl, rawInternalToken, detectWorkspaceRoot(), allowLoopbackServiceUrlInProd);
    }

    public AiServiceSettings(
            Environment environment,
            String rawServiceUrl,
            String rawInternalToken) {
        this(environment, rawServiceUrl, rawInternalToken, detectWorkspaceRoot(), false);
    }

    AiServiceSettings(
            Environment environment,
            String rawServiceUrl,
            String rawInternalToken,
            Path workspaceRoot) {
        this(environment, rawServiceUrl, rawInternalToken, workspaceRoot, false);
    }

    AiServiceSettings(
            Environment environment,
            String rawServiceUrl,
            String rawInternalToken,
            Path workspaceRoot,
            boolean allowLoopbackServiceUrlInProd) {
        this.environment = environment;
        this.rawServiceUrl = rawServiceUrl;
        this.rawInternalToken = rawInternalToken;
        this.workspaceRoot = workspaceRoot;
        this.allowLoopbackServiceUrlInProd = allowLoopbackServiceUrlInProd;
    }

    public String getResolvedServiceUrl() {
        if (StringUtils.hasText(rawServiceUrl)) {
            return rawServiceUrl.trim();
        }
        if (isDevOrLocalProfile()) {
            return LOCAL_DEV_AI_SERVICE_URL;
        }
        return "";
    }

    public String getResolvedInternalToken() {
        if (StringUtils.hasText(rawInternalToken)) {
            String resolved = rawInternalToken.trim();
            if (isDevOrLocalProfile() && LOCAL_DEV_AI_INTERNAL_TOKEN.equals(resolved)) {
                String envFileToken = resolveLocalEnvFileToken();
                if (StringUtils.hasText(envFileToken)) {
                    return envFileToken;
                }
            }
            return resolved;
        }
        if (isDevOrLocalProfile()) {
            String envFileToken = resolveLocalEnvFileToken();
            if (StringUtils.hasText(envFileToken)) {
                return envFileToken;
            }
            return LOCAL_DEV_AI_INTERNAL_TOKEN;
        }
        return "";
    }

    public List<String> getResolvedInternalTokenCandidates() {
        String primaryToken = getResolvedInternalToken();
        if (!StringUtils.hasText(primaryToken)) {
            return List.of();
        }

        if (!isDevOrLocalProfile() || LOCAL_DEV_AI_INTERNAL_TOKEN.equals(primaryToken)) {
            return List.of(primaryToken);
        }

        return List.of(primaryToken, LOCAL_DEV_AI_INTERNAL_TOKEN);
    }

    public String buildUrl(String path) {
        String baseUrl = getResolvedServiceUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    public String sanitizedServiceTarget() {
        String baseUrl = getResolvedServiceUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "not-configured";
        }

        try {
            URI uri = URI.create(baseUrl.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return "invalid-url";
            }

            String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme() : "unknown";
            int port = uri.getPort();
            return port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
        } catch (IllegalArgumentException e) {
            return "invalid-url";
        }
    }

    public boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    public boolean isDevOrLocalProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile));
    }

    public boolean isUsingFallbackServiceUrl() {
        return !StringUtils.hasText(rawServiceUrl) && isDevOrLocalProfile();
    }

    public boolean isUsingFallbackInternalToken() {
        return !StringUtils.hasText(rawInternalToken) && isDevOrLocalProfile();
    }

    public String activeProfilesLabel() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return "default";
        }
        return String.join(",", activeProfiles);
    }

    public void validateForStartup() {
        if (!isProdProfile()) {
            return;
        }

        if (!StringUtils.hasText(getResolvedServiceUrl())) {
            throw new IllegalStateException(
                    "prod profile requires ai.service-url (set AI_SERVICE_URL)");
        }

        if (!StringUtils.hasText(getResolvedInternalToken())) {
            throw new IllegalStateException(
                    "prod profile requires ai.internal-token (set AI_INTERNAL_TOKEN)");
        }

        if (!allowLoopbackServiceUrlInProd && isLoopbackServiceUrl(getResolvedServiceUrl())) {
            throw new IllegalStateException(
                    "prod profile rejects loopback ai.service-url; set AI_SERVICE_URL to the AI service host or explicitly set app.ai.proxy.allow-loopback-service-url-in-prod=true");
        }
    }

    private boolean isLoopbackServiceUrl(String serviceUrl) {
        try {
            URI uri = URI.create(serviceUrl.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }

            String normalizedHost = host.trim().toLowerCase();
            return "localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "::1".equals(normalizedHost)
                    || "[::1]".equals(normalizedHost)
                    || "0:0:0:0:0:0:0:1".equals(normalizedHost);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String resolveLocalEnvFileToken() {
        for (Path relativePath : LOCAL_TOKEN_ENV_PATHS) {
            String value = readEnvFileValue(workspaceRoot.resolve(relativePath), "AI_INTERNAL_TOKEN");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String readEnvFileValue(Path path, String key) {
        if (path == null || !Files.exists(path)) {
            return "";
        }

        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!trimmed.startsWith(key + "=")) {
                    continue;
                }
                String value = trimmed.substring((key + "=").length()).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (IOException ignored) {
            return "";
        }

        return "";
    }

    private static Path detectWorkspaceRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("docker-compose.yml")) || Files.exists(current.resolve(".env.prod"))) {
                return current;
            }
            current = current.getParent();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }
}
