package com.example.BegaDiary.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost",
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5176",
            "http://localhost:8080",
            "http://127.0.0.1",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5176");

    @Value("${app.allowed-origins:http://localhost,http://localhost:3000,http://localhost:5173,http://localhost:5176,http://localhost:8080,http://127.0.0.1,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:5176}")
    private String allowedOriginsStr;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        List<String> origins = Arrays.stream(allowedOriginsStr == null ? new String[0] : allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> !origin.equals("*"))
                .toList();

        if (origins.isEmpty()) {
            origins = DEFAULT_ALLOWED_ORIGINS;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>(DEFAULT_ALLOWED_ORIGINS);
        merged.addAll(origins);

        registry.addMapping("/api/**")
                .allowedOrigins()
                .allowedOriginPatterns(merged.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
