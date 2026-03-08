package com.example.ai.config;

import java.io.IOException;
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
    private static final List<String> LOCAL_TOKEN_ENV_FILES = List.of(".env", ".env.prod");

    private final Environment environment;
    private final String rawServiceUrl;
    private final String rawInternalToken;
    private final Path workspaceRoot;

    @Autowired
    public AiServiceSettings(
            Environment environment,
            @org.springframework.beans.factory.annotation.Value("${ai.service-url:}") String rawServiceUrl,
            @org.springframework.beans.factory.annotation.Value("${ai.internal-token:}") String rawInternalToken) {
        this(environment, rawServiceUrl, rawInternalToken, detectWorkspaceRoot());
    }

    AiServiceSettings(
            Environment environment,
            String rawServiceUrl,
            String rawInternalToken,
            Path workspaceRoot) {
        this.environment = environment;
        this.rawServiceUrl = rawServiceUrl;
        this.rawInternalToken = rawInternalToken;
        this.workspaceRoot = workspaceRoot;
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

    public String buildUrl(String path) {
        String baseUrl = getResolvedServiceUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
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
    }

    private String resolveLocalEnvFileToken() {
        for (String filename : LOCAL_TOKEN_ENV_FILES) {
            String value = readEnvFileValue(workspaceRoot.resolve(filename), "AI_INTERNAL_TOKEN");
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
