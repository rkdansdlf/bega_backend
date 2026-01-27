package com.example.BegaDiary.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOriginsStr;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        String[] origins = allowedOriginsStr.split(",");
        registry.addMapping("/api/**")
                .allowedOrigins(java.util.Objects.requireNonNull(origins))
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}