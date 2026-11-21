package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.auth.dto.OAuth2StateData;
import com.example.auth.dto.OAuth2StateStorageData;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

class OAuth2StateServiceRedisIntegrationTest {

    private static final long OWNER_ID = 88L;
    private static final long OTHER_USER_ID = 99L;
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    @TempDir
    Path redisDirectory;

    private Process redisProcess;
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private OAuth2StateService service;

    @BeforeEach
    void startRedis() throws Exception {
        boolean available = redisServerAvailable();
        // In CI a missing redis-server must fail the build instead of silently skipping these checks.
        if (!available && System.getenv("CI") != null) {
            fail("redis-server is required in CI for OAuth2 state integration tests");
        }
        assumeTrue(available, "redis-server is required for this integration test");

        int port = findFreePort();
        redisProcess = new ProcessBuilder(
                "redis-server",
                "--bind", "127.0.0.1",
                "--port", Integer.toString(port),
                "--protected-mode", "yes",
                "--save", "",
                "--appendonly", "no",
                "--daemonize", "no",
                "--dir", redisDirectory.toString(),
                "--logfile", redisDirectory.resolve("redis.log").toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();

        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", port));
        redisTemplate = new StringRedisTemplate(connectionFactory);
        userRepository = mock(UserRepository.class);
        AuthSecurityMonitoringService securityMonitoringService = mock(AuthSecurityMonitoringService.class);
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        connectionFactory.afterPropertiesSet();
        redisTemplate.afterPropertiesSet();
        awaitRedis(redisTemplate, redisProcess);

        service = new OAuth2StateService(redisTemplate, objectMapper, userRepository, securityMonitoringService);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisProcess != null) {
            stopRedis(redisProcess);
        }
    }

    @Test
    void consumeStateDeletesExpiredPrimaryWithoutReplayAndAllowsValidOwnerReplay() throws Exception {
        String expiredStateId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                primaryKey(expiredStateId),
                objectMapper.writeValueAsString(new OAuth2StateStorageData(
                        77L, System.currentTimeMillis() - Duration.ofMinutes(6).toMillis())));

        assertThat(service.consumeState(expiredStateId)).isNull();
        assertThat(redisTemplate.hasKey(primaryKey(expiredStateId))).isFalse();
        assertThat(redisTemplate.hasKey(replayKey(expiredStateId))).isFalse();
        verify(userRepository, never()).findById(77L);

        String validStateId = storeValidState(OWNER_ID, System.currentTimeMillis());
        stubUser(OWNER_ID);

        OAuth2StateData consumed = service.consumeState(validStateId);
        OAuth2StateData replayed = service.consumeState(validStateId, OWNER_ID);

        assertThat(consumed).isNotNull();
        assertThat(replayed).isEqualTo(consumed);
        assertThat(redisTemplate.hasKey(primaryKey(validStateId))).isFalse();
        assertThat(redisTemplate.opsForValue().get(replayKey(validStateId))).isNotBlank();
        Long replayTtlSeconds = redisTemplate.getExpire(replayKey(validStateId));
        assertThat(replayTtlSeconds).isBetween(1L, 60L);
        verify(userRepository, times(2)).findById(OWNER_ID);
    }

    @Test
    void secondUnauthenticatedConsumeReturnsNullOnRealRedis() throws Exception {
        String stateId = storeValidState(OWNER_ID, System.currentTimeMillis());
        stubUser(OWNER_ID);

        assertThat(service.consumeState(stateId)).isNotNull();
        assertThat(service.consumeState(stateId)).isNull();

        // The anonymous replay is rejected before any user lookup, so only the first consume reads the user.
        verify(userRepository, times(1)).findById(OWNER_ID);
    }

    @Test
    void replayIsRejectedForDifferentOwnerAndAnonymousCaller() throws Exception {
        String stateId = storeValidState(OWNER_ID, System.currentTimeMillis());
        stubUser(OWNER_ID);
        assertThat(service.consumeState(stateId)).isNotNull();

        assertThat(service.consumeState(stateId, OTHER_USER_ID)).isNull();
        assertThat(service.consumeState(stateId, null)).isNull();

        // Rejected replays must not consume the replay marker, so the owner can still read the result.
        assertThat(redisTemplate.hasKey(replayKey(stateId))).isTrue();
        assertThat(service.consumeState(stateId, OWNER_ID)).isNotNull();
    }

    @Test
    void replayMarkerStopsWorkingOnceItsTtlElapses() throws Exception {
        String stateId = storeValidState(OWNER_ID, System.currentTimeMillis());
        stubUser(OWNER_ID);
        assertThat(service.consumeState(stateId)).isNotNull();

        redisTemplate.expire(replayKey(stateId), Duration.ofSeconds(1));
        Thread.sleep(1_500L);

        assertThat(service.consumeState(stateId, OWNER_ID)).isNull();
    }

    @Test
    void stateAtFiveMinuteBoundaryIsAcceptedAndJustPastItIsRejected() throws Exception {
        stubUser(OWNER_ID);

        String withinTtl = storeValidState(OWNER_ID, System.currentTimeMillis() - Duration.ofSeconds(299).toMillis());
        assertThat(service.consumeState(withinTtl)).isNotNull();

        String pastTtl = storeValidState(OWNER_ID, System.currentTimeMillis() - Duration.ofSeconds(301).toMillis());
        assertThat(service.consumeState(pastTtl)).isNull();
        assertThat(redisTemplate.hasKey(primaryKey(pastTtl))).isFalse();
        assertThat(redisTemplate.hasKey(replayKey(pastTtl))).isFalse();
    }

    @Test
    void malformedPayloadIsDeletedWithoutReplayMarker() {
        String stateId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(primaryKey(stateId), "not-json", STATE_TTL);

        assertThat(service.consumeState(stateId, OWNER_ID)).isNull();
        assertThat(redisTemplate.hasKey(primaryKey(stateId))).isFalse();
        assertThat(redisTemplate.hasKey(replayKey(stateId))).isFalse();
    }

    @Test
    void payloadWithoutCreatedAtIsDeletedWithoutReplayMarker() {
        String stateId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(primaryKey(stateId), "{\"userId\":" + OWNER_ID + "}", STATE_TTL);

        assertThat(service.consumeState(stateId)).isNull();
        assertThat(redisTemplate.hasKey(primaryKey(stateId))).isFalse();
        assertThat(redisTemplate.hasKey(replayKey(stateId))).isFalse();
    }

    @Test
    void concurrentConsumersYieldExactlyOneWinner() throws Exception {
        String stateId = storeValidState(OWNER_ID, System.currentTimeMillis());
        stubUser(OWNER_ID);

        int contenders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OAuth2StateData>> results = new ArrayList<>();
        try {
            for (int i = 0; i < contenders; i++) {
                Callable<OAuth2StateData> consume = () -> {
                    start.await();
                    return service.consumeState(stateId);
                };
                results.add(pool.submit(consume));
            }
            start.countDown();

            int winners = 0;
            for (Future<OAuth2StateData> result : results) {
                if (result.get(10, TimeUnit.SECONDS) != null) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private String storeValidState(long userId, long createdAt) throws Exception {
        String stateId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                primaryKey(stateId),
                objectMapper.writeValueAsString(new OAuth2StateStorageData(userId, createdAt)),
                STATE_TTL);
        return stateId;
    }

    private void stubUser(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("owner@example.test");
        user.setName("OAuth Owner");
        user.setRole("ROLE_USER");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private static String primaryKey(String stateId) {
        return "oauth2:state:" + stateId;
    }

    private static String replayKey(String stateId) {
        return "oauth2:state-replay:" + stateId;
    }

    private boolean redisServerAvailable() throws InterruptedException {
        Process probe;
        try {
            probe = new ProcessBuilder("redis-server", "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException missingBinary) {
            return false;
        }
        boolean finished = probe.waitFor(3, TimeUnit.SECONDS);
        if (!finished) {
            probe.destroyForcibly();
            return false;
        }
        return probe.exitValue() == 0;
    }

    private int findFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private void awaitRedis(StringRedisTemplate template, Process process) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && process.isAlive()) {
            try {
                String pingResponse = template.execute((RedisCallback<String>) connection -> connection.ping());
                if ("PONG".equals(pingResponse)) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // Redis may still be starting; retry until the bounded deadline.
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("Local redis-server did not become ready");
    }

    private void stopRedis(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(3, TimeUnit.SECONDS);
        }
    }
}
