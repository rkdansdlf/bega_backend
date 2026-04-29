package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("AllowedOriginResolver tests")
class AllowedOriginResolverTest {

    @Test
    @DisplayName("prod profile should keep canonical origins when no extras are configured")
    void resolvesCanonicalProdOriginsWithoutConfiguredExtras() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AllowedOriginResolver resolver = new AllowedOriginResolver(
                environment,
                "",
                false);

        assertThat(resolver.resolve())
                .containsExactly("https://www.begabaseball.xyz", "https://begabaseball.xyz");
    }

    @Test
    @DisplayName("prod profile should filter pages.dev origins unless preview access is enabled")
    void resolvesCanonicalAndConfiguredProdOrigins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AllowedOriginResolver resolver = new AllowedOriginResolver(
                environment,
                "https://www.begabaseball.xyz,https://*.frontend-dfl.pages.dev,https://preview.example",
                false);

        assertThat(resolver.resolve())
                .containsExactly(
                        "https://www.begabaseball.xyz",
                        "https://begabaseball.xyz",
                        "https://preview.example");
    }

    @Test
    @DisplayName("prod profile should keep pages.dev origins when preview access is enabled")
    void resolvesConfiguredPreviewOriginsWhenEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AllowedOriginResolver resolver = new AllowedOriginResolver(
                environment,
                "https://www.begabaseball.xyz,https://*.frontend-dfl.pages.dev,https://preview.example",
                true);

        assertThat(resolver.resolve())
                .containsExactly(
                        "https://www.begabaseball.xyz",
                        "https://begabaseball.xyz",
                        "https://*.frontend-dfl.pages.dev",
                        "https://preview.example");
    }

    @Test
    @DisplayName("local profile should keep loopback and host.docker.internal defaults")
    void resolvesLoopbackDefaultsForLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        AllowedOriginResolver resolver = new AllowedOriginResolver(
                environment,
                "",
                false);

        List<String> origins = resolver.resolve();

        assertThat(origins)
                .contains("http://localhost:5176")
                .contains("http://127.0.0.1:5176")
                .contains("http://host.docker.internal:5177");
        assertThat(origins)
                .doesNotContain("https://begabaseball.xyz")
                .doesNotContain("https://*.frontend-dfl.pages.dev");
    }
}
