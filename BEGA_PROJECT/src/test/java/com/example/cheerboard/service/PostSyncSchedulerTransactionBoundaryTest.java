package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostHotScoreProjection;
import com.example.cheerboard.repo.CheerPostRepo;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSyncSchedulerTransactionBoundaryTest {

    @Mock
    private RedisPostService redisPostService;

    @Mock
    private CheerPostRepo postRepo;

    @Mock
    private CheerService cheerService;

    @Mock
    private JobScheduler jobScheduler;

    @Test
    void redisBatchIsNotAcknowledgedWhenDatabaseTransactionCommitFails() {
        long postId = 42L;
        FailingNthCommitTransactionManager transactionManager = new FailingNthCommitTransactionManager(2);
        CheerPostViewSyncService viewSyncService = transactionalProxy(
                new CheerPostViewSyncService(postRepo), transactionManager);
        PostSyncScheduler scheduler = new PostSyncScheduler(
                redisPostService, viewSyncService, cheerService, jobScheduler, true);
        when(redisPostService.getDirtyPostIds()).thenReturn(Set.of(postId));
        when(postRepo.findViewSyncWatermarkById(postId)).thenReturn(Optional.of(0L));
        when(redisPostService.claimViewCountSyncBatch(postId, 0L))
                .thenReturn(Optional.of(new RedisPostService.ViewCountSyncBatch(1L, 5)));
        when(postRepo.applyViewCountSyncBatch(postId, 1L, 5)).thenReturn(1);
        when(postRepo.findHotScoreProjectionById(postId)).thenReturn(Optional.of(
                new TestHotScoreProjection(postId, 10, 3, 2, 1, Instant.parse("2026-04-05T09:00:00Z"))));

        scheduler.syncViewCounts();

        org.assertj.core.api.Assertions.assertThat(transactionManager.commitCount()).isEqualTo(2);
        verify(redisPostService).claimViewCountSyncBatch(postId, 0L);
        verify(redisPostService, never()).acknowledgeViewCountSyncBatch(postId, 1L);
        verify(cheerService, never()).updateHotScoreForSync(org.mockito.ArgumentMatchers.any(CheerPost.class));
    }

    private CheerPostViewSyncService transactionalProxy(
            CheerPostViewSyncService target,
            FailingNthCommitTransactionManager transactionManager) {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        return (CheerPostViewSyncService) proxyFactory.getProxy();
    }

    private static class FailingNthCommitTransactionManager extends AbstractPlatformTransactionManager {

        private final int failingCommitNumber;
        private int commitCount;

        private FailingNthCommitTransactionManager(int failingCommitNumber) {
            this.failingCommitNumber = failingCommitNumber;
        }

        private int commitCount() {
            return commitCount;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
            if (commitCount == failingCommitNumber) {
                throw new IllegalStateException("simulated database commit failure");
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private record TestHotScoreProjection(
            Long id,
            int views,
            int likeCount,
            int commentCount,
            int repostCount,
            Instant createdAt) implements CheerPostHotScoreProjection {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public int getViews() {
            return views;
        }

        @Override
        public int getLikeCount() {
            return likeCount;
        }

        @Override
        public int getCommentCount() {
            return commentCount;
        }

        @Override
        public int getRepostCount() {
            return repostCount;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}
