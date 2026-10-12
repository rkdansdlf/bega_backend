package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostHotScoreProjection;
import java.time.Instant;
import java.util.Optional;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSyncSchedulerTest {

    @Mock
    private RedisPostService redisPostService;

    @Mock
    private CheerPostViewSyncService viewSyncService;

    @Mock
    private CheerService cheerService;

    @Mock
    private JobScheduler jobScheduler;

    @Test
    @DisplayName("view sync는 full CheerPost entity 대신 hot-score projection만 조회한다")
    void processPostSyncUsesHotScoreProjectionInsteadOfFullEntityLoad() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, viewSyncService, cheerService, jobScheduler, true);
        Long postId = 42L;

        RedisPostService.ViewCountSyncBatch batch = new RedisPostService.ViewCountSyncBatch(11L, 7);
        when(viewSyncService.getViewSyncWatermark(postId)).thenReturn(10L);
        when(redisPostService.claimViewCountSyncBatch(postId, 10L)).thenReturn(Optional.of(batch));
        when(viewSyncService.applyBatch(postId, 11L, 7)).thenReturn(Optional.of(new TestHotScoreProjection(
                postId,
                17,
                3,
                2,
                1,
                Instant.parse("2026-04-05T09:00:00Z"))));

        scheduler.processPostSync(postId);

        verify(viewSyncService).applyBatch(postId, 11L, 7);
        verify(redisPostService).acknowledgeViewCountSyncBatch(postId, 11L);
        inOrder(viewSyncService, cheerService, redisPostService)
                .verify(viewSyncService).applyBatch(postId, 11L, 7);
        inOrder(viewSyncService, cheerService, redisPostService)
                .verify(cheerService).updateHotScoreForSync(any(CheerPost.class));
        inOrder(viewSyncService, cheerService, redisPostService)
                .verify(redisPostService).acknowledgeViewCountSyncBatch(postId, 11L);

        ArgumentCaptor<CheerPost> postCaptor = ArgumentCaptor.forClass(CheerPost.class);
        verify(cheerService).updateHotScoreForSync(postCaptor.capture());
        CheerPost hotScorePost = postCaptor.getValue();
        assertThat(hotScorePost.getId()).isEqualTo(postId);
        assertThat(hotScorePost.getViews()).isEqualTo(17);
        assertThat(hotScorePost.getLikeCount()).isEqualTo(3);
        assertThat(hotScorePost.getCommentCount()).isEqualTo(2);
        assertThat(hotScorePost.getRepostCount()).isEqualTo(1);
        assertThat(hotScorePost.getCreatedAt()).isEqualTo(Instant.parse("2026-04-05T09:00:00Z"));
    }

    @Test
    @DisplayName("DB 반영 대상이 없는 batch도 Redis에서 ack해 재시도를 막는다")
    void processPostSyncClearsDeltaWhenPostNoLongerExists() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, viewSyncService, cheerService, jobScheduler, true);
        Long postId = 42L;

        when(viewSyncService.getViewSyncWatermark(postId)).thenReturn(4L);
        when(redisPostService.claimViewCountSyncBatch(postId, 4L))
                .thenReturn(Optional.of(new RedisPostService.ViewCountSyncBatch(5L, 7)));
        when(viewSyncService.applyBatch(postId, 5L, 7)).thenReturn(Optional.empty());

        scheduler.processPostSync(postId);

        verify(redisPostService).acknowledgeViewCountSyncBatch(postId, 5L);
        verifyNoInteractions(cheerService);
    }

    @Test
    @DisplayName("Redis에 동기화할 batch가 없으면 orphan dirty marker를 정리한다")
    void processPostSyncClearsZeroDeltaMarker() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, viewSyncService, cheerService, jobScheduler, true);
        Long postId = 42L;

        when(viewSyncService.getViewSyncWatermark(postId)).thenReturn(0L);
        when(redisPostService.claimViewCountSyncBatch(postId, 0L)).thenReturn(Optional.empty());

        scheduler.processPostSync(postId);

        verify(redisPostService).clearDirtyPostIfNoSyncWork(postId);
        verifyNoInteractions(cheerService);
    }

    @Test
    @DisplayName("DB watermark 조회 오류는 전파하고 Redis batch를 claim하지 않는다")
    void processPostSyncDoesNotClaimWhenViewSyncWatermarkReadFails() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, viewSyncService, cheerService, jobScheduler, true);
        Long postId = 42L;

        RuntimeException databaseFailure = new IllegalStateException("DB unavailable");
        when(viewSyncService.getViewSyncWatermark(postId)).thenThrow(databaseFailure);

        assertThatThrownBy(() -> scheduler.processPostSync(postId)).isSameAs(databaseFailure);

        verify(redisPostService, never()).claimViewCountSyncBatch(postId, 0L);
        verify(redisPostService, never()).acknowledgeViewCountSyncBatch(postId, 0L);
        verifyNoInteractions(cheerService);
    }

    @Test
    @DisplayName("Redis 조회 오류면 dirty data를 보존한다")
    void processPostSyncKeepsDirtyDataWhenViewCountReadFails() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, viewSyncService, cheerService, jobScheduler, true);
        Long postId = 42L;
        RuntimeException redisFailure = new IllegalStateException("Redis unavailable");

        when(viewSyncService.getViewSyncWatermark(postId)).thenReturn(0L);
        when(redisPostService.claimViewCountSyncBatch(postId, 0L)).thenThrow(redisFailure);

        assertThatThrownBy(() -> scheduler.processPostSync(postId))
                .isSameAs(redisFailure);
        verify(redisPostService, never()).acknowledgeViewCountSyncBatch(any(), anyLong());
        verifyNoInteractions(cheerService);
    }

    @Test
    @DisplayName("hot 점수 갱신 실패 시 Redis batch ack를 보류해 재시도 가능하게 한다")
    void processPostSyncKeepsBatchWhenHotScoreRefreshFails() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, viewSyncService, cheerService, jobScheduler, true);
        Long postId = 42L;
        IllegalStateException hotScoreFailure = new IllegalStateException("Redis hot score update failed");
        when(viewSyncService.getViewSyncWatermark(postId)).thenReturn(10L);
        when(redisPostService.claimViewCountSyncBatch(postId, 10L))
                .thenReturn(Optional.of(new RedisPostService.ViewCountSyncBatch(11L, 7)));
        when(viewSyncService.applyBatch(postId, 11L, 7)).thenReturn(Optional.of(new TestHotScoreProjection(
                postId, 17, 3, 2, 1, Instant.parse("2026-04-05T09:00:00Z"))));
        doThrow(hotScoreFailure).when(cheerService).updateHotScoreForSync(any(CheerPost.class));

        assertThatThrownBy(() -> scheduler.processPostSync(postId)).isSameAs(hotScoreFailure);

        verify(redisPostService, never()).acknowledgeViewCountSyncBatch(postId, 11L);
    }

    @Test
    @DisplayName("post sync scheduler가 비활성화되면 recurrent jobs를 등록하지 않는다")
    void runSkipsRecurringJobsWhenDisabled() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, viewSyncService, cheerService, jobScheduler, false);

        scheduler.run(org.mockito.Mockito.mock(ApplicationArguments.class));

        verifyNoInteractions(jobScheduler);
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
