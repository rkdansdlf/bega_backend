package com.example.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("SecurityStartupValidator tests")
class SecurityStartupValidatorTest {

    @Test
    @DisplayName("dev profile should fail fast when oauth2 cookie secret is missing")
    void failsWhenDevCookieSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment,
                false,
                "");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAUTH2_COOKIE_SECRET");
    }

    @Test
    @DisplayName("local profile should accept runtime auth validation when cookie secret is present")
    void passesForLocalWhenCookieSecretExists() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment,
                false,
                "local-cookie-secret");

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod profile should still require secure cookies")
    void failsWhenProdSecureCookieDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment,
                false,
                "prod-cookie-secret");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.cookie.secure=true");
    }

    @Test
    @DisplayName("test profile should skip runtime auth validation")
    void skipsValidationForTestProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment,
                false,
                "");

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }
}
