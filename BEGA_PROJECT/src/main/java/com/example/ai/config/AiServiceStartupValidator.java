package com.example.ai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceStartupValidator implements ApplicationRunner {

    private final AiServiceSettings aiServiceSettings;

    @Override
    public void run(ApplicationArguments args) {
        aiServiceSettings.validateForStartup();

        if (aiServiceSettings.isProdProfile()) {
            log.info("AI startup validation passed for prod profile.");
            return;
        }

        if (aiServiceSettings.isUsingFallbackServiceUrl() || aiServiceSettings.isUsingFallbackInternalToken()) {
            log.info(
                    "AI startup fallback enabled. profiles={} serviceUrlFallback={} internalTokenFallback={}",
                    aiServiceSettings.activeProfilesLabel(),
                    aiServiceSettings.isUsingFallbackServiceUrl(),
                    aiServiceSettings.isUsingFallbackInternalToken());
        }
    }
}
