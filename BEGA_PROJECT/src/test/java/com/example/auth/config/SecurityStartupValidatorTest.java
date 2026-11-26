package com.example.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("SecurityStartupValidator tests")
class SecurityStartupValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = { "dev", "dev-adb", "local" })
    @DisplayName("prod profile should reject every development profile during Spring context startup")
    void rejectsProdCombinedWithDevelopmentProfiles(String developmentProfile) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod", developmentProfile);
            context.register(SecurityStartupValidator.class);

            assertThatThrownBy(context::refresh)
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause()
                    .hasMessageContaining("prod")
                    .hasMessageContaining(developmentProfile);
        }
    }

    @Test
    @DisplayName("profile guard should also reject mixed-case profile names")
    void rejectsMixedCaseProfileNames() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("PROD", "DEV-ADB");

        assertThatThrownBy(() -> new SecurityStartupValidator(
                environment,
                true,
                "configured-cookie-secret",
                "configured-refresh-token-pepper-value",
                true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEV-ADB");
    }

    @Test
    @DisplayName("profile guard should remain eager when global lazy initialization is enabled")
    void validatorIsExplicitlyEager() {
        Lazy lazy = SecurityStartupValidator.class.getAnnotation(Lazy.class);

        assertThat(lazy).isNotNull();
        assertThat(lazy.value()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "prod", "dev", "dev-adb", "local", "test" })
    @DisplayName("individual profiles should remain accepted by profile composition guard")
    void acceptsProfilesWhenNotMixed(String activeProfile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);

        assertThatCode(() -> new SecurityStartupValidator(
                environment,
                true,
                "configured-cookie-secret",
                "configured-refresh-token-pepper-value",
                true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev profile should fail fast when oauth2 cookie secret is missing")
    void failsWhenDevCookieSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment,
                false,
                "",
                "",
                false);

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
                "local-cookie-secret",
                "",
                false);

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
                "prod-cookie-secret",
                "strong-prod-refresh-token-pepper-value",
                true);

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
                "",
                "",
                false);

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod profile should fail fast when refresh token pepper is missing")
    void failsWhenProdRefreshTokenPepperIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment,
                true,
                "prod-cookie-secret",
                "",
                true);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_REFRESH_TOKEN_PEPPER");
    }

    @Test
    @DisplayName("prod profile should fail fast when mail is disabled")
    void failsWhenProdMailIsDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment,
                true,
                "prod-cookie-secret",
                "strong-prod-refresh-token-pepper-value",
                false);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MAIL_ENABLED=true");
    }

    // ---- prod 에서 JobRunr 대시보드를 켜는 경우: 기본 인증 강제 ----

    private SecurityStartupValidator prodValidator(MockEnvironment environment) {
        environment.setActiveProfiles("prod");
        return new SecurityStartupValidator(
                environment,
                true,
                "prod-cookie-secret",
                "strong-prod-refresh-token-pepper-value",
                true);
    }

    @Test
    @DisplayName("prod: 대시보드가 꺼져 있으면(기본값) 인증 설정 없이도 통과한다")
    void prodWithDashboardDisabledPasses() {
        MockEnvironment environment = new MockEnvironment();

        assertThatCode(() -> prodValidator(environment).run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod: 대시보드를 켜면서 인증 설정이 없으면 기동을 거부한다")
    void prodWithUnauthenticatedDashboardIsRejected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jobrunr.dashboard.enabled", "true");

        assertThatThrownBy(() -> prodValidator(environment).run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JOBRUNR_DASHBOARD_USERNAME")
                .hasMessageContaining("JOBRUNR_DASHBOARD_PASSWORD");
    }

    @Test
    @DisplayName("prod: 사용자만 있고 비밀번호가 없거나 짧으면 거부한다")
    void prodWithIncompleteDashboardCredentialsIsRejected() {
        MockEnvironment missingPassword = new MockEnvironment()
                .withProperty("jobrunr.dashboard.enabled", "true")
                .withProperty("jobrunr.dashboard.username", "ops");
        MockEnvironment shortPassword = new MockEnvironment()
                .withProperty("jobrunr.dashboard.enabled", "true")
                .withProperty("jobrunr.dashboard.username", "ops")
                .withProperty("jobrunr.dashboard.password", "short");

        assertThatThrownBy(() -> prodValidator(missingPassword).run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> prodValidator(shortPassword).run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12");
    }

    @Test
    @DisplayName("prod: 대시보드를 켜더라도 기본 인증이 충분하면 통과한다")
    void prodWithAuthenticatedDashboardPasses() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jobrunr.dashboard.enabled", "true")
                .withProperty("jobrunr.dashboard.username", "ops")
                .withProperty("jobrunr.dashboard.password", "a-long-dashboard-password");

        assertThatCode(() -> prodValidator(environment).run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev: 대시보드 인증 강제는 prod 에만 적용된다")
    void devWithUnauthenticatedDashboardStillPasses() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jobrunr.dashboard.enabled", "true");
        environment.setActiveProfiles("dev");
        SecurityStartupValidator validator = new SecurityStartupValidator(
                environment, false, "dev-cookie-secret", "", false);

        assertThatCode(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod: 운영자가 실제로 쓰는 환경 변수(JOBRUNR_DASHBOARD_*)로 설정해도 검증기가 읽는다")
    void prodReadsDashboardSettingsFromEnvironmentVariables() {
        StandardEnvironment withoutAuth = new StandardEnvironment();
        withoutAuth.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "testEnv", Map.of("JOBRUNR_DASHBOARD_ENABLED", "true")));
        withoutAuth.setActiveProfiles("prod");
        StandardEnvironment withAuth = new StandardEnvironment();
        withAuth.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "testEnv", Map.of(
                        "JOBRUNR_DASHBOARD_ENABLED", "true",
                        "JOBRUNR_DASHBOARD_USERNAME", "ops",
                        "JOBRUNR_DASHBOARD_PASSWORD", "a-long-dashboard-password")));
        withAuth.setActiveProfiles("prod");

        assertThatThrownBy(() -> new SecurityStartupValidator(withoutAuth, true, "c", "strong-prod-refresh-token-pepper-value", true)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JOBRUNR_DASHBOARD_USERNAME");
        assertThatCode(() -> new SecurityStartupValidator(withAuth, true, "c", "strong-prod-refresh-token-pepper-value", true)
                .run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }
}
