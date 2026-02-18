package com.example.common.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost",
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5176",
            "http://localhost:*",
            "http://localhost:8080",
            "http://127.0.0.1",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:*",
            "http://127.0.0.1:5176");

    @Value("${app.allowed-origins:http://localhost,http://localhost:3000,http://localhost:5173,http://localhost:5176,http://localhost:*,http://localhost:8080,http://127.0.0.1,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:*,http://127.0.0.1:5176}")
    private String allowedOriginsStr;

    private List<String> parseAllowedOrigins() {
        List<String> parsed = Arrays.stream(allowedOriginsStr == null ? new String[0] : allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (parsed.isEmpty()) {
            return DEFAULT_ALLOWED_ORIGINS;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>(DEFAULT_ALLOWED_ORIGINS);
        merged.addAll(parsed);
        return List.copyOf(merged);
    }

    @Override
    public void addCorsMappings(@org.springframework.lang.NonNull CorsRegistry registry) {
        List<String> allowedOrigins = parseAllowedOrigins();

        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
