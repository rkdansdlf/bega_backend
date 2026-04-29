package com.example.auth.config;

import com.example.auth.service.CaptchaVerifier;
import com.example.auth.service.NoOpCaptchaVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaVerifierConfig {

    @Bean
    @ConditionalOnMissingBean(CaptchaVerifier.class)
    public CaptchaVerifier captchaVerifier(
            @Value("${app.auth.captcha.enabled:false}") boolean enabled) {
        return new NoOpCaptchaVerifier(enabled);
    }
}
