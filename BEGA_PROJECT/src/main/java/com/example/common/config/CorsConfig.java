package com.example.common.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final AllowedOriginResolver allowedOriginResolver;

    @Override
    public void addCorsMappings(@org.springframework.lang.NonNull CorsRegistry registry) {
        List<String> allowedOrigins = allowedOriginResolver.resolve();

        registry.addMapping("/**")
                .allowedOrigins()
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
