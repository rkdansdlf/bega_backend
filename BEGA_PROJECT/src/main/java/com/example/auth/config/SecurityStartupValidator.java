package com.example.auth.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Lazy(false)
@Component
public class SecurityStartupValidator implements ApplicationRunner {

    private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "dev-adb", "local");

    private final Environment environment;
    private final boolean secureCookie;
    private final String oauth2CookieSecret;
    private final String refreshTokenPepper;
    private final boolean mailEnabled;

    public SecurityStartupValidator(
            Environment environment,
            @Value("${app.cookie.secure:false}") boolean secureCookie,
            @Value("${app.oauth2.cookie-secret:}") String oauth2CookieSecret,
            @Value("${app.auth.refresh-token-pepper:}") String refreshTokenPepper,
            @Value("${app.mail.enabled:false}") boolean mailEnabled) {
        rejectMixedRuntimeProfiles(environment);
        this.environment = environment;
        this.secureCookie = secureCookie;
        this.oauth2CookieSecret = oauth2CookieSecret;
        this.refreshTokenPepper = refreshTokenPepper;
        this.mailEnabled = mailEnabled;
    }

    private static void rejectMixedRuntimeProfiles(Environment environment) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean productionActive = activeProfiles.stream().anyMatch("prod"::equalsIgnoreCase);
        List<String> activeDevelopmentProfiles = activeProfiles.stream()
                .filter(profile -> DEVELOPMENT_PROFILES.contains(profile.toLowerCase(Locale.ROOT)))
                .toList();

        if (productionActive && !activeDevelopmentProfiles.isEmpty()) {
            throw new IllegalStateException(
                    "Incompatible active profiles: prod cannot be combined with development profile(s) "
                            + activeDevelopmentProfiles);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .toList();
        boolean requiresRuntimeAuthValidation = activeProfiles.stream()
                .anyMatch(profile -> "prod".equals(profile)
                        || "dev".equals(profile)
                        || "local".equals(profile));

        if (!requiresRuntimeAuthValidation) {
            return;
        }

        if (activeProfiles.contains("prod") && !secureCookie) {
            throw new IllegalStateException(
                    "prod profile requires app.cookie.secure=true (set profile-specific config for HTTPS cookies)");
        }

        if (!StringUtils.hasText(oauth2CookieSecret)) {
            throw new IllegalStateException(
                    "dev/local/prod profiles require app.oauth2.cookie-secret (set OAUTH2_COOKIE_SECRET)");
        }

        if (activeProfiles.contains("prod") && (!StringUtils.hasText(refreshTokenPepper)
                || refreshTokenPepper.length() < 32)) {
            throw new IllegalStateException(
                    "prod profile requires APP_REFRESH_TOKEN_PEPPER with at least 32 characters");
        }

        if (activeProfiles.contains("prod") && !mailEnabled) {
            throw new IllegalStateException(
                    "prod profile requires APP_MAIL_ENABLED=true for OAuth email challenges");
        }

        log.info("Security startup validation passed for runtime auth profile(s): {}", activeProfiles);
    }
}
