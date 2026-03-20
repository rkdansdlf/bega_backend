package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("AllowedOriginResolver tests")
class AllowedOriginResolverTest {

    @Test
    @DisplayName("prod profile should allow only the canonical www origin")
    void resolvesOnlyCanonicalProdOrigin() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AllowedOriginResolver resolver = new AllowedOriginResolver(
                environment,
                "https://www.begabaseball.xyz,https://begabaseball.xyz,https://preview.example");

        assertThat(resolver.resolve())
                .containsExactly("https://www.begabaseball.xyz");
    }

    @Test
    @DisplayName("local profile should keep loopback and host.docker.internal defaults")
    void resolvesLoopbackDefaultsForLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        AllowedOriginResolver resolver = new AllowedOriginResolver(
                environment,
                "");

        List<String> origins = resolver.resolve();

        assertThat(origins)
                .contains("http://localhost:5173")
                .contains("http://127.0.0.1:4173")
                .contains("http://host.docker.internal:5173");
        assertThat(origins)
                .doesNotContain("https://begabaseball.xyz")
                .doesNotContain("https://*.frontend-dfl.pages.dev");
    }
}
