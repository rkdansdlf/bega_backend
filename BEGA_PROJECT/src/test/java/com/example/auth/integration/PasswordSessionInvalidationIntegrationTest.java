package com.example.auth.integration;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:password_session_invalidation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.repositories.enabled=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
@DisplayName("Password session invalidation integration tests")
class PasswordSessionInvalidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshRepository refreshRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWTUtil jwtUtil;

    private UserEntity user;
    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("pw" + suffix)
                .name("Password Test User")
                .email("password+" + suffix + "@test.com")
                .password(passwordEncoder.encode("CurrentPass1!"))
                .role("ROLE_USER")
                .provider("LOCAL")
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .build());

        accessToken = jwtUtil.createJwt(user.getEmail(), user.getRole(), user.getId(), 60 * 60 * 1000L, 0);
        refreshToken = jwtUtil.createRefreshToken(user.getEmail(), user.getRole(), user.getId(), 0);

        RefreshToken persistedRefresh = new RefreshToken();
        persistedRefresh.setEmail(user.getEmail());
        persistedRefresh.setToken(refreshToken);
        persistedRefresh.setExpiryDate(LocalDateTime.now().plusDays(7));
        persistedRefresh.setDeviceType("desktop");
        persistedRefresh.setDeviceLabel("Test Browser");
        persistedRefresh.setBrowser("Chrome");
        persistedRefresh.setOs("macOS");
        persistedRefresh.setIp("127.0.0.1");
        persistedRefresh.setLastSeenAt(LocalDateTime.now());
        refreshRepository.save(persistedRefresh);
    }

    @AfterEach
    void tearDown() {
        if (user != null && user.getEmail() != null) {
            refreshRepository.deleteByEmail(user.getEmail());
        }
        if (user != null && user.getId() != null && userRepository.existsById(user.getId())) {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    @DisplayName("stale access token is rejected after token version increments")
    void staleAccessTokenIsRejectedAfterTokenVersionIncrement() throws Exception {
        user.setTokenVersion(1);
        userRepository.saveAndFlush(user);
        refreshRepository.deleteAll(refreshRepository.findAllByEmail(user.getEmail()));

        mockMvc.perform(get("/api/auth/mypage")
                        .cookie(new Cookie("Authorization", accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_AUTHOR"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("stale refresh token cannot be reissued after password-style session invalidation")
    void staleRefreshTokenCannotBeReissuedAfterSessionInvalidation() throws Exception {
        user.setTokenVersion(1);
        userRepository.saveAndFlush(user);
        refreshRepository.deleteAll(refreshRepository.findAllByEmail(user.getEmail()));

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new Cookie("Refresh", refreshToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("잘못된 Refresh Token입니다."));
    }
}
