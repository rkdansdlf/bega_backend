package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class CheerFeedServiceTestSupport {

    private CheerFeedServiceTestSupport() {
    }

    static CheerPost createSimplePost(Long postId, Long authorId) {
        UserEntity author = UserEntity.builder().id(authorId).name("Author-" + authorId).build();
        return CheerPost.builder()
                .id(postId)
                .author(author)
                .content("Post-" + postId)
                .postType(PostType.NORMAL)
                .build();
    }

    static CheerPost createRepostPost(Long postId, Long authorId, Long originalPostId) {
        CheerPost originalPost = createSimplePost(originalPostId, authorId + 1000);
        return CheerPost.builder()
                .id(postId)
                .author(UserEntity.builder().id(authorId).name("Author-" + authorId).build())
                .content("Repost-" + postId)
                .postType(PostType.NORMAL)
                .repostType(CheerPost.RepostType.QUOTE)
                .repostOf(originalPost)
                .build();
    }

    static double awaitActiveFeedEnrichmentCount(CheerFeedService feedService) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        double activeCount;
        do {
            Double observed = ReflectionTestUtils.invokeMethod(
                    feedService,
                    "activeFeedEnrichmentBulkheadPermits");
            activeCount = observed == null ? Double.NaN : observed;
            if (activeCount == 0.0d) {
                return activeCount;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        } while (System.nanoTime() < deadlineNanos);
        return activeCount;
    }

    @SuppressWarnings("unchecked")
    static <T> CompletableFuture<T> invokeEnrichmentAsync(
            CheerFeedService feedService,
            Supplier<T> supplier,
            T fallback) {
        return ReflectionTestUtils.invokeMethod(
                feedService,
                "supplyEnrichmentAsync",
                supplier,
                fallback);
    }

    static final class CountingDirectExecutorService extends AbstractExecutorService {
        private final AtomicInteger executeCount = new AtomicInteger();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor is shut down");
            }
            executeCount.incrementAndGet();
            command.run();
        }

        int executeCount() {
            return executeCount.get();
        }
    }
}
