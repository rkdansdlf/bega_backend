package com.example.BegaDiary.Config;

import com.example.common.config.AllowedOriginResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AllowedOriginResolver allowedOriginResolver;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        List<String> origins = allowedOriginResolver.resolve();

        registry.addMapping("/api/**")
                .allowedOrigins()
                .allowedOriginPatterns(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
