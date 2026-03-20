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

    public SecurityStartupValidator(
            Environment environment,
            @Value("${app.cookie.secure:false}") boolean secureCookie,
            @Value("${app.oauth2.cookie-secret:}") String oauth2CookieSecret) {
        this.environment = environment;
        this.secureCookie = secureCookie;
        this.oauth2CookieSecret = oauth2CookieSecret;
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

        log.info("Security startup validation passed for runtime auth profile(s): {}", activeProfiles);
    }
}
