package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.service.UserService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostChangesResponse;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.profile.storage.service.ProfileImageService;
import com.example.cheerboard.service.CheerFeedServiceTestSupport.CountingDirectExecutorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.awaitActiveFeedEnrichmentCount;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.createRepostPost;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.createSimplePost;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.invokeEnrichmentAsync;


@ExtendWith(MockitoExtension.class)
class CheerFeedServiceHotPostsTest extends CheerFeedServiceTestFixture {

    @Test
    @DisplayName("enrichment bulkhead gauge를 metrics service에 등록한다")
    void registerFeedEnrichmentBulkheadMetrics_registersGaugeSuppliers() {
        feedService.registerFeedEnrichmentBulkheadMetrics();

        verify(metricsService).registerFeedEnrichmentBulkheadMetrics(eq(feedService), any(), any());
    }

    @Test
    @DisplayName("HOT 목록은 Redis 랭킹 순서를 그대로 유지해야 한다")
    void getHotPosts_preservesRedisOrder() {
        // Given
        PageRequest pageable = PageRequest.of(0, 3);
        LinkedHashSet<Long> hotIds = new LinkedHashSet<>(List.of(3L, 1L, 2L));

        CheerPost post1 = createSimplePost(1L, 101L);
        CheerPost post2 = createSimplePost(2L, 102L);
        CheerPost post3 = createSimplePost(3L, 103L);

        when(redisPostService.getHotPostIds(0, 2, PopularFeedAlgorithm.TIME_DECAY)).thenReturn(hotIds);
        when(redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY)).thenReturn(3L);
        when(postRepo.findAllByIdWithGraph(hotIds)).thenReturn(List.of(post1, post2, post3)); // Repo might return in
                                                                                              // any order

        // FeedService calls interactionService for bulk optimizations
        // user is null (anonymous), so interactionService calls are skipped

        when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
        when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());
        CountingDirectExecutorService countingExecutor = new CountingDirectExecutorService();
        feedService.setFeedEnrichmentExecutorForTest(countingExecutor);

        when(postDtoMapper.toPostSummaryRes(
                any(CheerPost.class),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyInt(),
                anyList(),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap()))
                .thenAnswer(invocation -> {
                    CheerPost post = invocation.getArgument(0);
                    return PostSummaryRes.of(
                            post.getId(),
                            "LG",
                            "LG 트윈스",
                            "LG",
                            "#C30452",
                            post.getContent(),
                            "author",
                            "author",
                            null,
                            null,
                            null,
                            post.getCommentCount(),
                            post.getLikeCount(),
                            0,
                            false,
                            post.getViews(),
                            true,
                            false,
                            false,
                            post.getRepostCount(),
                            false,
                            "NORMAL",
                            List.of());
                });

        // When
        Page<PostSummaryRes> page = feedService.getHotPosts(pageable, "TIME_DECAY", null);

        // Then
        assertThat(page.getContent())
                .extracting(PostSummaryRes::id)
                .containsExactly(3L, 1L, 2L);
        assertThat(countingExecutor.executeCount()).isEqualTo(4);
        verify(metricsService).recordFeedRequest(
                eq("hot"),
                eq(false),
                isNull(),
                eq("TIME_DECAY"),
                eq(false),
                eq(3),
                eq("success"),
                longThat(value -> value >= 0));
    }

    @Test
    @DisplayName("HOT 목록에 리포스트가 있을 때만 원본 이미지 prefetch task를 실행한다")
    void getHotPosts_prefetchesRepostOriginalImagesOnlyForReposts() {
        PageRequest pageable = PageRequest.of(0, 1);
        LinkedHashSet<Long> hotIds = new LinkedHashSet<>(List.of(31L));
        CheerPost repost = createRepostPost(31L, 131L, 900L);

        when(redisPostService.getHotPostIds(0, 0, PopularFeedAlgorithm.TIME_DECAY)).thenReturn(hotIds);
        when(redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY)).thenReturn(1L);
        when(postRepo.findAllByIdWithGraph(hotIds)).thenReturn(List.of(repost));
        when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
        when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());
        CountingDirectExecutorService countingExecutor = new CountingDirectExecutorService();
        feedService.setFeedEnrichmentExecutorForTest(countingExecutor);
        when(postDtoMapper.toPostSummaryRes(
                any(CheerPost.class),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyInt(),
                anyList(),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap()))
                .thenReturn(PostSummaryRes.of(
                        31L,
                        "LG",
                        "LG 트윈스",
                        "LG",
                        "#C30452",
                        "repost",
                        "author",
                        "author",
                        null,
                        null,
                        null,
                        0,
                        0,
                        0,
                        false,
                        0,
                        true,
                        false,
                        false,
                        0,
                        false,
                        "NORMAL",
                        List.of()));

        Page<PostSummaryRes> page = feedService.getHotPosts(pageable, "TIME_DECAY", null);

        assertThat(page.getContent()).hasSize(1);
        assertThat(countingExecutor.executeCount()).isEqualTo(5);
        verify(imageService).getPostImageUrlsByPostIds(List.of(31L));
        verify(imageService).getPostImageUrlsByPostIds(List.of(900L));
    }


    @Test
    @DisplayName("HYBRID hot 목록은 stale/ineligible Redis 엔트리를 정리하고 total을 갱신한다")
    void getHotPosts_hybridPrunesInvalidEntriesAndRefreshesTotal() {
        PageRequest pageable = PageRequest.of(0, 3);
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>(List.of(9L, 8L, 7L));

        CheerPost post9 = createSimplePost(9L, 109L);
        CheerPost post8 = createSimplePost(8L, 108L);
        CheerPost post7 = createSimplePost(7L, 107L);

        when(redisPostService.getHotPostIds(0, 202, PopularFeedAlgorithm.TIME_DECAY)).thenReturn(candidateIds);
        when(redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY)).thenReturn(3L, 0L);
        when(postRepo.findAllByIdWithGraph(candidateIds)).thenReturn(List.of(post9, post8, post7));
        when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
        when(popularFeedScoringService.isHotEligible(any(CheerPost.class), anyInt(), any())).thenReturn(false);

        Page<PostSummaryRes> page = feedService.getHotPosts(pageable, "HYBRID", null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(redisPostService).removeFromHotList(9L);
        verify(redisPostService).removeFromHotList(8L);
        verify(redisPostService).removeFromHotList(7L);
        verify(postDtoMapper, never()).toPostSummaryRes(
                any(CheerPost.class),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyInt(),
                anyList(),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap());
    }

    @Test
    @DisplayName("HYBRID hot 목록은 게스트에게 비공개인 작성자의 게시글을 HOT Redis에서 정리한다")
    void getHotPosts_hybridPrunesGuestInvisibleEntries() {
        PageRequest pageable = PageRequest.of(0, 3);
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>(List.of(21L, 22L));

        CheerPost post21 = createSimplePost(21L, 121L);
        CheerPost post22 = createSimplePost(22L, 122L);

        when(redisPostService.getHotPostIds(0, 202, PopularFeedAlgorithm.TIME_DECAY)).thenReturn(candidateIds);
        when(redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY)).thenReturn(2L, 0L);
        when(postRepo.findAllByIdWithGraph(candidateIds)).thenReturn(List.of(post21, post22));
        when(publicVisibilityVerifier.canAccess(post21.getAuthor(), null)).thenReturn(false);
        when(publicVisibilityVerifier.canAccess(post22.getAuthor(), null)).thenReturn(false);

        Page<PostSummaryRes> page = feedService.getHotPosts(pageable, "HYBRID", null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(redisPostService).removeFromHotList(21L);
        verify(redisPostService).removeFromHotList(22L);
        verify(postDtoMapper, never()).toPostSummaryRes(
                any(CheerPost.class),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyInt(),
                anyList(),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap());
    }

    @Test
    @DisplayName("HYBRID hot 목록은 삭제된 게시글만 남아 있으면 Redis total도 함께 정리한다")
    void getHotPosts_hybridPrunesMissingEntriesBeforeEmptyReturn() {
        PageRequest pageable = PageRequest.of(0, 3);
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>(List.of(220L, 3L, 170L));

        when(redisPostService.getHotPostIds(0, 202, PopularFeedAlgorithm.TIME_DECAY)).thenReturn(candidateIds);
        when(redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY)).thenReturn(3L, 0L);
        when(postRepo.findAllByIdWithGraph(candidateIds)).thenReturn(Collections.emptyList());

        Page<PostSummaryRes> page = feedService.getHotPosts(pageable, "HYBRID", null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(redisPostService).removeFromHotList(220L);
        verify(redisPostService).removeFromHotList(3L);
        verify(redisPostService).removeFromHotList(170L);
    }

}
