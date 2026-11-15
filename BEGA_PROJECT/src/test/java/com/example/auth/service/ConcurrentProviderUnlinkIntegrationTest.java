package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import com.example.common.exception.BadRequestBusinessException;
import com.example.kbo.repository.TeamRepository;
import com.example.media.service.MediaLinkService;
import com.example.profile.storage.service.ProfileImageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(UserService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrent_provider_unlink;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrentProviderUnlinkIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProviderRepository userProviderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TeamRepository teamRepository;

    @MockitoBean
    private RefreshRepository refreshRepository;

    @MockitoBean
    private UserFollowRepository userFollowRepository;

    @MockitoBean
    private UserBlockRepository userBlockRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JWTUtil jwtUtil;

    @MockitoBean
    private ProfileImageService profileImageService;

    @MockitoBean
    private MediaLinkService mediaLinkService;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @MockitoBean
    private AccountSecurityService accountSecurityService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private LoginAttemptService loginAttemptService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void concurrentUnlinkOfTwoProvidersPreservesOneLoginMethod() throws Exception {
        UserEntity user = createUserWithTwoProviders();
        CountDownLatch firstUnlinkApplied = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondUnlinkStarted = new CountDownLatch(1);
        AtomicInteger secondSessionId = new AtomicInteger(-1);

        Future<?> first = executor.submit(() -> inTransaction(() -> {
            userService.unlinkProvider(user.getId(), "google");
            firstUnlinkApplied.countDown();
            await(releaseFirstTransaction);
            return null;
        }));
        assertThat(firstUnlinkApplied.await(5, TimeUnit.SECONDS)).isTrue();

        Future<String> second = executor.submit(() -> {
            try {
                return inTransaction(() -> {
                    secondSessionId.set(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class));
                    secondUnlinkStarted.countDown();
                    userService.unlinkProvider(user.getId(), "kakao");
                    return "success";
                });
            } catch (BadRequestBusinessException expected) {
                return expected.getCode();
            }
        });
        assertThat(secondUnlinkStarted.await(5, TimeUnit.SECONDS)).isTrue();
        awaitDatabaseLockWait(secondSessionId.get());
        assertThat(second.isDone()).isFalse();

        releaseFirstTransaction.countDown();
        first.get(5, TimeUnit.SECONDS);

        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("LOGIN_METHOD_REQUIRED");
        assertThat(userProviderRepository.findByUserId(user.getId()))
                .extracting(UserProvider::getProvider)
                .containsExactly("kakao");
    }

    private UserEntity createUserWithTwoProviders() {
        UserEntity user = userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@unlink" + UUID.randomUUID().toString().substring(0, 6))
                .name("Synthetic unlink user")
                .email(UUID.randomUUID() + "@synthetic.test")
                .role("ROLE_USER")
                .password(null)
                .build());
        userProviderRepository.saveAndFlush(UserProvider.builder()
                .user(user)
                .provider("google")
                .providerId("google-" + user.getId())
                .build());
        userProviderRepository.saveAndFlush(UserProvider.builder()
                .user(user)
                .provider("kakao")
                .providerId("kakao-" + user.getId())
                .build());
        return user;
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for synthetic transaction release");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void awaitDatabaseLockWait(int sessionId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            Integer blockedSessionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.SESSIONS
                    WHERE SESSION_ID = ? AND BLOCKER_ID IS NOT NULL
                    """, Integer.class, sessionId);
            if (blockedSessionCount != null && blockedSessionCount > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Contending unlink session never reported waiting on the user lock");
    }
}
