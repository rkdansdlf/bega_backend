package com.example.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoOpCaptchaVerifier implements CaptchaVerifier {

    private final boolean enabled;

    public NoOpCaptchaVerifier(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean verify(String token, HttpServletRequest request) {
        if (enabled) {
            log.warn("CAPTCHA is enabled but no CaptchaVerifier provider is configured");
        }
        return !enabled;
    }
}
