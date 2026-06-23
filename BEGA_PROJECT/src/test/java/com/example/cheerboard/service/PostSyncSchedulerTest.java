package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostHotScoreProjection;
import com.example.cheerboard.repo.CheerPostRepo;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSyncSchedulerTest {

    @Mock
    private RedisPostService redisPostService;

    @Mock
    private CheerPostRepo postRepo;

    @Mock
    private CheerService cheerService;

    @Mock
    private JobScheduler jobScheduler;

    @Test
    @DisplayName("view sync는 full CheerPost entity 대신 hot-score projection만 조회한다")
    void processPostSyncUsesHotScoreProjectionInsteadOfFullEntityLoad() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, postRepo, cheerService, jobScheduler, true);
        Long postId = 42L;

        when(redisPostService.getViewCount(postId)).thenReturn(7);
        when(postRepo.findHotScoreProjectionById(postId)).thenReturn(Optional.of(new TestHotScoreProjection(
                postId,
                10,
                3,
                2,
                1,
                Instant.parse("2026-04-05T09:00:00Z"))));

        scheduler.processPostSync(postId);

        verify(postRepo, never()).findById(postId);
        verify(postRepo).incrementViewCountByDelta(postId, 7);
        verify(redisPostService).clearDirtyPost(postId, 7);

        ArgumentCaptor<CheerPost> postCaptor = ArgumentCaptor.forClass(CheerPost.class);
        verify(cheerService).updateHotScore(postCaptor.capture());
        CheerPost hotScorePost = postCaptor.getValue();
        assertThat(hotScorePost.getId()).isEqualTo(postId);
        assertThat(hotScorePost.getViews()).isEqualTo(17);
        assertThat(hotScorePost.getLikeCount()).isEqualTo(3);
        assertThat(hotScorePost.getCommentCount()).isEqualTo(2);
        assertThat(hotScorePost.getRepostCount()).isEqualTo(1);
        assertThat(hotScorePost.getCreatedAt()).isEqualTo(Instant.parse("2026-04-05T09:00:00Z"));
    }

    @Test
    @DisplayName("post sync scheduler가 비활성화되면 recurrent jobs를 등록하지 않는다")
    void runSkipsRecurringJobsWhenDisabled() {
        PostSyncScheduler scheduler = new PostSyncScheduler(redisPostService, postRepo, cheerService, jobScheduler, false);

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
