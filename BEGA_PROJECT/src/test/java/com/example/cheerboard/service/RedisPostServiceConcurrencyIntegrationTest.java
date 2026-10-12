package com.example.cheerboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.example.common.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisPostServiceConcurrencyIntegrationTest {

    @TempDir
    Path redisDirectory;

    @Test
    void simultaneousViewsFromOneUserIncrementAndRegisterDirtyPostOnce() throws Exception {
        assumeTrue(redisServerAvailable(), "redis-server is required for this integration test");

        int port = findFreePort();
        Path logPath = redisDirectory.resolve("redis.log");
        Process redisProcess = new ProcessBuilder(
                "redis-server",
                "--bind", "127.0.0.1",
                "--port", Integer.toString(port),
                "--protected-mode", "yes",
                "--save", "",
                "--appendonly", "no",
                "--daemonize", "no",
                "--dir", redisDirectory.toString(),
                "--logfile", logPath.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", port));
        RedisTemplate<String, Object> redisTemplate = newRedisTemplate(connectionFactory);
        ExecutorService executor = Executors.newFixedThreadPool(24);
        long previouslyViewedPostId = positiveRandomId();
        long newPostId = positiveRandomId();
        long zeroBoundaryPostId = positiveRandomId();
        long positiveRemainderPostId = positiveRandomId();
        long userId = positiveRandomId();
        long priorZeroBoundaryViewerId = positiveRandomId();
        long concurrentZeroBoundaryViewerId = positiveRandomId();
        long priorRemainderViewerId = positiveRandomId();
        long concurrentRemainderViewerId = positiveRandomId();
        String previouslyViewedUsersKey = "post:viewed:" + previouslyViewedPostId;
        String previouslyViewedCountKey = "post:views:" + previouslyViewedPostId;
        String previouslyViewedBatchKey = "post:views:sync:inflight:" + previouslyViewedPostId;
        String previouslyViewedSequenceKey = "post:views:sync:sequence:" + previouslyViewedPostId;
        String newPostViewedUsersKey = "post:viewed:" + newPostId;
        String newPostViewCountKey = "post:views:" + newPostId;
        String newPostBatchKey = "post:views:sync:inflight:" + newPostId;
        String newPostSequenceKey = "post:views:sync:sequence:" + newPostId;
        String zeroBoundaryViewedUsersKey = "post:viewed:" + zeroBoundaryPostId;
        String zeroBoundaryViewCountKey = "post:views:" + zeroBoundaryPostId;
        String positiveRemainderViewedUsersKey = "post:viewed:" + positiveRemainderPostId;
        String positiveRemainderViewCountKey = "post:views:" + positiveRemainderPostId;
        String zeroBoundaryBatchKey = "post:views:sync:inflight:" + zeroBoundaryPostId;
        String zeroBoundarySequenceKey = "post:views:sync:sequence:" + zeroBoundaryPostId;
        String positiveRemainderBatchKey = "post:views:sync:inflight:" + positiveRemainderPostId;
        String positiveRemainderSequenceKey = "post:views:sync:sequence:" + positiveRemainderPostId;
        String dirtyPostsKey = "posts:dirty:views";

        try {
            connectionFactory.afterPropertiesSet();
            redisTemplate.afterPropertiesSet();
            awaitRedis(port, redisProcess, logPath);

            RedisPostService service = new RedisPostService(redisTemplate);
            redisTemplate.opsForSet().add(previouslyViewedUsersKey, Long.toString(userId));
            redisTemplate.opsForValue().increment(previouslyViewedCountKey);
            redisTemplate.opsForSet().add(dirtyPostsKey, Long.toString(previouslyViewedPostId));

            runConcurrentViews(executor, service, previouslyViewedPostId, userId);
            runConcurrentViews(executor, service, newPostId, userId);

            assertThat(redisTemplate.opsForValue().get(previouslyViewedCountKey).toString()).isEqualTo("1");
            assertThat(redisTemplate.opsForSet().members(previouslyViewedUsersKey))
                    .containsExactly(Long.toString(userId));
            assertThat(redisTemplate.opsForValue().get(newPostViewCountKey).toString()).isEqualTo("1");
            assertThat(redisTemplate.opsForSet().members(newPostViewedUsersKey))
                    .containsExactly(Long.toString(userId));
            Set<String> dirtyPostIds = redisTemplate.opsForSet().members(dirtyPostsKey).stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            assertThat(dirtyPostIds).containsExactlyInAnyOrder(
                    Long.toString(previouslyViewedPostId), Long.toString(newPostId));

            RedisPostService.ViewCountSyncBatch previousBatch =
                    service.claimViewCountSyncBatch(previouslyViewedPostId, 0L).orElseThrow();
            assertThat(previousBatch).isEqualTo(new RedisPostService.ViewCountSyncBatch(1L, 1));
            assertThat(service.acknowledgeViewCountSyncBatch(previouslyViewedPostId, 1L)).isTrue();
            assertThat(redisTemplate.hasKey(previouslyViewedSequenceKey)).isFalse();

            RedisPostService.ViewCountSyncBatch firstBatch =
                    service.claimViewCountSyncBatch(newPostId, 0L).orElseThrow();
            assertThat(firstBatch).isEqualTo(new RedisPostService.ViewCountSyncBatch(1L, 1));
            assertThat(service.claimViewCountSyncBatch(newPostId, 500L)).contains(firstBatch);
            assertThat(redisTemplate.opsForValue().get(newPostViewCountKey)).isNull();
            assertThat(rawRedisString(redisTemplate, newPostBatchKey)).isEqualTo("1|1");

            service.incrementViewCount(newPostId, positiveRandomId());
            assertThat(redisTemplate.opsForValue().get(newPostViewCountKey).toString()).isEqualTo("1");
            assertThat(redisTemplate.getExpire(newPostViewCountKey)).isEqualTo(-1L);
            assertThat(service.acknowledgeViewCountSyncBatch(newPostId, 0L)).isFalse();
            assertThat(rawRedisString(redisTemplate, newPostBatchKey)).isEqualTo("1|1");
            assertThat(service.acknowledgeViewCountSyncBatch(newPostId, 1L)).isTrue();
            assertThat(rawRedisString(redisTemplate, newPostBatchKey)).isNull();
            assertThat(redisTemplate.hasKey(newPostSequenceKey)).isFalse();
            assertThat(redisTemplate.opsForValue().get(newPostViewCountKey).toString()).isEqualTo("1");

            RedisPostService.ViewCountSyncBatch secondBatch =
                    service.claimViewCountSyncBatch(newPostId, 1L).orElseThrow();
            assertThat(secondBatch).isEqualTo(new RedisPostService.ViewCountSyncBatch(2L, 1));
            assertThat(service.acknowledgeViewCountSyncBatch(newPostId, 2L)).isTrue();
            assertThat(redisTemplate.opsForSet().members(dirtyPostsKey)).isEmpty();
            assertThat(redisTemplate.opsForValue().get(newPostViewCountKey)).isNull();

            redisTemplate.delete(newPostSequenceKey);
            service.incrementViewCount(newPostId, positiveRandomId());
            RedisPostService.ViewCountSyncBatch afterSequenceEviction =
                    service.claimViewCountSyncBatch(newPostId, 2L).orElseThrow();
            assertThat(afterSequenceEviction.sequence()).isEqualTo(3L);
            assertThat(service.acknowledgeViewCountSyncBatch(newPostId, 3L)).isTrue();

            long orphanPostId = positiveRandomId();
            redisTemplate.opsForSet().add(dirtyPostsKey, Long.toString(orphanPostId));
            service.clearDirtyPostIfNoSyncWork(orphanPostId);
            assertThat(redisTemplate.opsForSet().members(dirtyPostsKey)).isEmpty();

            redisTemplate.opsForSet().add(zeroBoundaryViewedUsersKey, Long.toString(priorZeroBoundaryViewerId));
            redisTemplate.opsForValue().increment(zeroBoundaryViewCountKey);
            redisTemplate.opsForSet().add(dirtyPostsKey, Long.toString(zeroBoundaryPostId));
            redisTemplate.opsForSet().add(
                    positiveRemainderViewedUsersKey, Long.toString(priorRemainderViewerId));
            redisTemplate.opsForValue().increment(positiveRemainderViewCountKey);
            redisTemplate.opsForValue().increment(positiveRemainderViewCountKey);
            redisTemplate.opsForSet().add(dirtyPostsKey, Long.toString(positiveRemainderPostId));

            RedisPostService.ViewCountSyncBatch zeroBoundaryBatch =
                    service.claimViewCountSyncBatch(zeroBoundaryPostId, 0L).orElseThrow();
            RedisPostService.ViewCountSyncBatch positiveRemainderBatch =
                    service.claimViewCountSyncBatch(positiveRemainderPostId, 0L).orElseThrow();
            runConcurrentViewAndAck(
                    executor, service, zeroBoundaryPostId, concurrentZeroBoundaryViewerId,
                    zeroBoundaryBatch.sequence());
            runConcurrentViewAndAck(
                    executor, service, positiveRemainderPostId, concurrentRemainderViewerId,
                    positiveRemainderBatch.sequence());

            assertThat(redisTemplate.opsForValue().get(zeroBoundaryViewCountKey).toString()).isEqualTo("1");
            assertThat(redisTemplate.getExpire(zeroBoundaryViewCountKey)).isEqualTo(-1L);
            assertThat(redisTemplate.opsForSet().members(zeroBoundaryViewedUsersKey))
                    .containsExactlyInAnyOrder(
                            Long.toString(priorZeroBoundaryViewerId),
                            Long.toString(concurrentZeroBoundaryViewerId));
            assertThat(redisTemplate.opsForValue().get(positiveRemainderViewCountKey).toString()).isEqualTo("1");
            assertThat(redisTemplate.opsForSet().members(positiveRemainderViewedUsersKey))
                    .containsExactlyInAnyOrder(
                            Long.toString(priorRemainderViewerId),
                            Long.toString(concurrentRemainderViewerId));
            Set<String> concurrentDirtyPostIds = redisTemplate.opsForSet().members(dirtyPostsKey).stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            assertThat(concurrentDirtyPostIds).containsExactlyInAnyOrder(
                    Long.toString(zeroBoundaryPostId), Long.toString(positiveRemainderPostId));

            RedisPostService.ViewCountSyncBatch zeroRemainderBatch =
                    service.claimViewCountSyncBatch(zeroBoundaryPostId, zeroBoundaryBatch.sequence()).orElseThrow();
            RedisPostService.ViewCountSyncBatch positiveRemainderBatchAfterAck =
                    service.claimViewCountSyncBatch(
                            positiveRemainderPostId, positiveRemainderBatch.sequence()).orElseThrow();
            assertThat(zeroRemainderBatch.delta()).isEqualTo(1);
            assertThat(positiveRemainderBatchAfterAck.delta()).isEqualTo(1);
            assertThat(service.acknowledgeViewCountSyncBatch(
                    zeroBoundaryPostId, zeroRemainderBatch.sequence())).isTrue();
            assertThat(service.acknowledgeViewCountSyncBatch(
                    positiveRemainderPostId, positiveRemainderBatchAfterAck.sequence())).isTrue();
            assertThat(redisTemplate.opsForSet().members(dirtyPostsKey)).isEmpty();
        } finally {
            executor.shutdownNow();
            try {
                redisTemplate.delete(List.of(
                        previouslyViewedUsersKey,
                        previouslyViewedCountKey,
                        previouslyViewedBatchKey,
                        previouslyViewedSequenceKey,
                        newPostViewedUsersKey,
                        newPostViewCountKey,
                        newPostBatchKey,
                        newPostSequenceKey,
                        zeroBoundaryViewedUsersKey,
                        zeroBoundaryViewCountKey,
                        zeroBoundaryBatchKey,
                        zeroBoundarySequenceKey,
                        positiveRemainderViewedUsersKey,
                        positiveRemainderViewCountKey,
                        positiveRemainderBatchKey,
                        positiveRemainderSequenceKey,
                        dirtyPostsKey));
            } catch (RuntimeException ignored) {
                // Cleanup must still stop the isolated Redis process if the server failed.
            } finally {
                try {
                    connectionFactory.destroy();
                } finally {
                    stopRedis(redisProcess);
                }
            }
        }
    }

    private RedisTemplate<String, Object> newRedisTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisSerializer serializer = new StringRedisSerializer();
        RedisSerializer<Object> valueSerializer = new RedisConfig()
                .redisValueSerializer(new ObjectMapper().findAndRegisterModules());
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(serializer);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashKeySerializer(serializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        return redisTemplate;
    }

    private String rawRedisString(RedisTemplate<String, Object> redisTemplate, String key) {
        byte[] value = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private void runConcurrentViews(
            ExecutorService executor,
            RedisPostService service,
            long postId,
            long userId) throws Exception {
        int concurrentRequests = 24;
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> calls = new ArrayList<>(concurrentRequests);

        for (int index = 0; index < concurrentRequests; index++) {
            calls.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent view calls");
                }
                service.incrementViewCount(postId, userId);
                return null;
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> call : calls) {
            call.get(10, TimeUnit.SECONDS);
        }
    }

    private void runConcurrentViewAndAck(
            ExecutorService executor,
            RedisPostService service,
            long postId,
            long newViewerId,
            long sequence) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> ack = executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent dirty-post ack");
            }
            service.acknowledgeViewCountSyncBatch(postId, sequence);
            return null;
        });
        Future<?> view = executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent view call");
            }
            service.incrementViewCount(postId, newViewerId);
            return null;
        });

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        ack.get(10, TimeUnit.SECONDS);
        view.get(10, TimeUnit.SECONDS);
    }

    private boolean redisServerAvailable() throws IOException, InterruptedException {
        Process probe = new ProcessBuilder("redis-server", "--version")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!probe.waitFor(2, TimeUnit.SECONDS)) {
            probe.destroyForcibly();
            return false;
        }
        return probe.exitValue() == 0;
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private void awaitRedis(int port, Process redisProcess, Path logPath) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && redisProcess.isAlive()) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
                return;
            } catch (IOException notReadyYet) {
                Thread.sleep(25);
            }
        }
        String log = java.nio.file.Files.exists(logPath)
                ? java.nio.file.Files.readString(logPath)
                : "Redis did not create a startup log";
        throw new IllegalStateException("Isolated Redis server did not start: " + log);
    }

    private void stopRedis(Process redisProcess) {
        redisProcess.destroy();
        try {
            if (!redisProcess.waitFor(3, TimeUnit.SECONDS)) {
                redisProcess.destroyForcibly();
                redisProcess.waitFor(3, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            redisProcess.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private long positiveRandomId() {
        long candidate = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return candidate == 0L ? 1L : candidate;
    }
}
