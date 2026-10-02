package com.example.common.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;

final class ReadOnlyVerificationTestEnvironment {

    static final String PROFILE = "local-readonly-verification";
    static final String PROFILE_RESOURCE = "src/main/resources/application-" + PROFILE + ".properties";

    private ReadOnlyVerificationTestEnvironment() {
    }

    static MockEnvironment create() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", PROFILE);
        try {
            environment.getPropertySources().addLast(new ResourcePropertySource(new FileSystemResource(PROFILE_RESOURCE)));
        } catch (IOException exception) {
            throw new AssertionError("Readonly profile resource is missing", exception);
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("READONLY_PRIMARY_DB_URL", "jdbc:postgresql://127.0.0.1:5432/readonly_fixture");
        inputs.put("READONLY_BASEBALL_DB_URL", "jdbc:postgresql://localhost:5432/readonly_fixture");
        for (String key : new String[] {
                "READONLY_PRIMARY_DB_USERNAME", "READONLY_PRIMARY_DB_PASSWORD",
                "READONLY_BASEBALL_DB_USERNAME", "READONLY_BASEBALL_DB_PASSWORD",
                "READONLY_JWT_SECRET", "READONLY_OAUTH2_COOKIE_SECRET", "READONLY_REFRESH_TOKEN_PEPPER",
                "READONLY_AI_INTERNAL_TOKEN", "READONLY_S3_ACCESS_KEY", "READONLY_S3_SECRET_KEY",
                "READONLY_S3_BUCKET", "READONLY_GOOGLE_CLIENT_ID", "READONLY_GOOGLE_CLIENT_SECRET",
                "READONLY_KAKAO_CLIENT_ID", "READONLY_KAKAO_CLIENT_SECRET",
                "READONLY_NAVER_CLIENT_ID", "READONLY_NAVER_CLIENT_SECRET" }) {
            inputs.put(key, "isolated-test-fixture-material-not-a-runtime-credential");
        }
        environment.getPropertySources().addLast(new MapPropertySource("operator-test-inputs", inputs));
        return environment;
    }
}
