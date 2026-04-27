package com.example.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebAsyncConfig implements WebMvcConfigurer {

    private final long requestTimeoutMs;

    public WebAsyncConfig(
            @Value("${APP_MVC_ASYNC_REQUEST_TIMEOUT_MS:180000}") long requestTimeoutMs) {
        this.requestTimeoutMs = Math.max(30_000L, requestTimeoutMs);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(requestTimeoutMs);
        log.info("Configured Spring MVC async request timeout={}ms", requestTimeoutMs);
    }
}
