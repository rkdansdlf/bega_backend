package com.example.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:cors_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "app.allowed-origins=https://www.begabaseball.xyz,https://begabaseball.xyz"
})
@DisplayName("CORS integration tests — SecurityConfig.corsConfigurationSource is sole authority")
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("preflight from www.begabaseball.xyz should pass")
    void preflight_wwwOrigin_passes() throws Exception {
        mockMvc.perform(options("/api/auth/mypage")
                        .header(HttpHeaders.ORIGIN, "https://www.begabaseball.xyz")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.begabaseball.xyz"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, Matchers.containsString("GET")));
    }

    @Test
    @DisplayName("preflight from begabaseball.xyz (apex) should pass")
    void preflight_apexOrigin_passes() throws Exception {
        mockMvc.perform(options("/api/auth/mypage")
                        .header(HttpHeaders.ORIGIN, "https://begabaseball.xyz")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://begabaseball.xyz"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, Matchers.containsString("GET")));
    }

    @Test
    @DisplayName("preflight from unknown origin should NOT include Access-Control-Allow-Origin")
    void preflight_unknownOrigin_rejected() throws Exception {
        mockMvc.perform(options("/api/auth/mypage")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("unauthenticated GET from apex origin should return 401 WITH CORS headers")
    void unauthenticatedGet_apexOrigin_returnsCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/mypage")
                        .header(HttpHeaders.ORIGIN, "https://begabaseball.xyz"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://begabaseball.xyz"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("CORS response should expose Authorization and Set-Cookie headers")
    void preflight_exposedHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/mypage")
                        .header(HttpHeaders.ORIGIN, "https://www.begabaseball.xyz")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        Matchers.allOf(
                                Matchers.containsString("Authorization"),
                                Matchers.containsString("Set-Cookie"))));
    }
}
