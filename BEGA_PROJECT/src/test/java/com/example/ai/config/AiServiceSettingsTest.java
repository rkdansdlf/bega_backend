package com.example.ai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServiceSettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void devProfileBlankValuesUseLocalFallbacks() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        AiServiceSettings settings = new AiServiceSettings(environment, "", "", tempDir);

        assertThat(settings.getResolvedServiceUrl()).isEqualTo(AiServiceSettings.LOCAL_DEV_AI_SERVICE_URL);
        assertThat(settings.getResolvedInternalToken()).isEqualTo(AiServiceSettings.LOCAL_DEV_AI_INTERNAL_TOKEN);
        assertThat(settings.isUsingFallbackServiceUrl()).isTrue();
        assertThat(settings.isUsingFallbackInternalToken()).isTrue();
    }

    @Test
    void devProfileBlankTokenUsesLocalEnvFallbackWhenAvailable() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        Files.writeString(tempDir.resolve(".env.local"), "AI_INTERNAL_TOKEN=env-local-token\n");

        AiServiceSettings settings = new AiServiceSettings(environment, "", "", tempDir);

        assertThat(settings.getResolvedInternalToken()).isEqualTo("env-local-token");
    }

    @Test
    void devProfileIgnoresEnvProdWhenResolvingBlankToken() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        Files.writeString(tempDir.resolve(".env.prod"), "AI_INTERNAL_TOKEN=env-prod-token\n");

        AiServiceSettings settings = new AiServiceSettings(environment, "", "", tempDir);

        assertThat(settings.getResolvedInternalToken()).isEqualTo(AiServiceSettings.LOCAL_DEV_AI_INTERNAL_TOKEN);
    }

    @Test
    void devProfileIgnoresEnvProdWhenDefaultLocalTokenIsConfigured() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        Files.writeString(tempDir.resolve(".env.prod"), "AI_INTERNAL_TOKEN=env-prod-token\n");

        AiServiceSettings settings = new AiServiceSettings(
                environment,
                "http://localhost:8001",
                AiServiceSettings.LOCAL_DEV_AI_INTERNAL_TOKEN,
                tempDir);

        assertThat(settings.getResolvedInternalToken()).isEqualTo(AiServiceSettings.LOCAL_DEV_AI_INTERNAL_TOKEN);
    }

    @Test
    void devProfilePrefersAiServiceLocalEnvOverWorkspaceLocalEnv() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        Files.createDirectories(tempDir.resolve("bega_AI"));
        Files.writeString(tempDir.resolve("bega_AI/.env.local"), "AI_INTERNAL_TOKEN=service-local-token\n");
        Files.writeString(tempDir.resolve(".env.local"), "AI_INTERNAL_TOKEN=workspace-local-token\n");
        Files.writeString(tempDir.resolve("bega_AI/.env"), "AI_INTERNAL_TOKEN=service-legacy-token\n");
        Files.writeString(tempDir.resolve(".env.prod"), "AI_INTERNAL_TOKEN=workspace-prod-token\n");

        AiServiceSettings settings = new AiServiceSettings(
                environment,
                "http://localhost:8001",
                AiServiceSettings.LOCAL_DEV_AI_INTERNAL_TOKEN,
                tempDir);

        assertThat(settings.getResolvedInternalToken()).isEqualTo("service-local-token");
    }

    @Test
    void prodProfileBlankServiceUrlFailsValidation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiServiceSettings settings = new AiServiceSettings(environment, "", "configured-token", tempDir);

        assertThatThrownBy(settings::validateForStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai.service-url");
    }

    @Test
    void prodProfileBlankInternalTokenFailsValidation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiServiceSettings settings = new AiServiceSettings(environment, "http://localhost:8001", "", tempDir);

        assertThatThrownBy(settings::validateForStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai.internal-token");
    }

    @Test
    void prodProfileLoopbackServiceUrlFailsValidation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiServiceSettings settings = new AiServiceSettings(environment, "http://localhost:8001", "configured-token", tempDir);

        assertThatThrownBy(settings::validateForStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback ai.service-url");
    }

    @Test
    void prodProfileLoopbackServiceUrlCanBeExplicitlyAllowed() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiServiceSettings settings = new AiServiceSettings(
                environment,
                "http://localhost:8001",
                "configured-token",
                tempDir,
                true);

        settings.validateForStartup();

        assertThat(settings.getResolvedServiceUrl()).isEqualTo("http://localhost:8001");
    }

    @Test
    void prodProfileExplicitValuesArePreserved() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiServiceSettings settings = new AiServiceSettings(
                environment,
                "https://ai.example.com",
                "prod-token",
                tempDir);

        settings.validateForStartup();

        assertThat(settings.getResolvedServiceUrl()).isEqualTo("https://ai.example.com");
        assertThat(settings.getResolvedInternalToken()).isEqualTo("prod-token");
        assertThat(settings.isUsingFallbackServiceUrl()).isFalse();
        assertThat(settings.isUsingFallbackInternalToken()).isFalse();
    }
}
