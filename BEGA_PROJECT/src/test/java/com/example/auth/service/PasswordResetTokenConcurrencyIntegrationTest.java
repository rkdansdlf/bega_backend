package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.dto.PasswordResetConfirmDto;
import com.example.auth.entity.PasswordResetToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PasswordResetService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_reset_token_concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetTokenConcurrencyIntegrationTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RefreshRepository refreshRepository;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    @Test
    void onlyOneConcurrentRequestCanConsumeTheSameResetToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserEntity user = userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("pwreset" + suffix)
                .name("Password Reset Concurrency Test")
                .email("password-reset-" + suffix + "@example.test")
                .password("old-encoded-password")
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .build());

        String rawToken = "synthetic-reset-" + UUID.randomUUID();
        String tokenHash = sha256Hex(rawToken);
        PasswordResetToken token = tokenRepository.saveAndFlush(PasswordResetToken.builder()
                .token(tokenHash)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build());

        CountDownLatch firstRequestReachedPasswordUpdate = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        CountDownLatch secondRequestReachedPasswordUpdate = new CountDownLatch(1);
        AtomicInteger encodeCalls = new AtomicInteger();
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> {
            if (encodeCalls.incrementAndGet() == 1) {
                firstRequestReachedPasswordUpdate.countDown();
                if (!releaseFirstRequest.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release the first synthetic reset");
                }
                return "encoded-first-password";
            }
            secondRequestReachedPasswordUpdate.countDown();
            return "encoded-second-password";
        });

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ResetAttempt> first = workers.submit(() -> attemptReset(rawToken, "FirstPassword1!"));
            assertThat(firstRequestReachedPasswordUpdate.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch secondRequestStarted = new CountDownLatch(1);
            Future<ResetAttempt> second = workers.submit(() -> {
                secondRequestStarted.countDown();
                return attemptReset(rawToken, "SecondPassword1!");
            });
            assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondRequestReachedPasswordUpdate.await(2, TimeUnit.SECONDS)).isFalse();

            releaseFirstRequest.countDown();
            ResetAttempt firstResult = first.get(10, TimeUnit.SECONDS);
            ResetAttempt secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstResult.succeeded(), secondResult.succeeded()))
                    .containsExactlyInAnyOrder(true, false);

            UserEntity updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getTokenVersion()).isEqualTo(1);
            assertThat(updatedUser.getPassword()).isEqualTo(
                    firstResult.succeeded() ? "encoded-first-password" : "encoded-second-password");

            PasswordResetToken consumedToken = tokenRepository.findById(token.getId()).orElseThrow();
            assertThat(consumedToken.isUsed()).isTrue();
            assertThat(consumedToken.getToken()).startsWith("used:");
            assertThat(tokenRepository.findByToken(tokenHash)).isEmpty();
            assertThat(encodeCalls.get()).isEqualTo(1);
            verify(refreshRepository).deleteByEmail(user.getEmail());
        } finally {
            releaseFirstRequest.countDown();
            workers.shutdownNow();
        }
    }

    private ResetAttempt attemptReset(String rawToken, String newPassword) {
        try {
            passwordResetService.confirmPasswordReset(
                    new PasswordResetConfirmDto(rawToken, newPassword, newPassword));
            return new ResetAttempt(true);
        } catch (IllegalArgumentException expected) {
            return new ResetAttempt(false);
        }
    }

    private String sha256Hex(String token) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record ResetAttempt(boolean succeeded) {
    }
}
