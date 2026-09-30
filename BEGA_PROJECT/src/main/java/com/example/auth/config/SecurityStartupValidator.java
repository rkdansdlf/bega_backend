package com.example.auth.config;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class SecurityStartupValidator implements ApplicationRunner {

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
        this.environment = environment;
        this.secureCookie = secureCookie;
        this.oauth2CookieSecret = oauth2CookieSecret;
        this.refreshTokenPepper = refreshTokenPepper;
        this.mailEnabled = mailEnabled;
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
