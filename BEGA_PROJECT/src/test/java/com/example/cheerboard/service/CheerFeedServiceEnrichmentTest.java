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
class CheerFeedServiceEnrichmentTest extends CheerFeedServiceTestFixture {

    @Test
    @DisplayName("listLightweight도 같은 작성자의 피드 프로필 URL 정규화를 페이지 안에서 한 번만 수행한다")
    void listLightweight_deduplicatesFeedProfileImageResolutionByAuthor() {
        PageRequest pageable = PageRequest.of(0, 2);
        UserEntity author = UserEntity.builder()
                .id(89L)
                .name("Light Author")
                .handle("light-author")
                .profileImageUrl("profiles/89/profile.webp")
                .profileFeedImageUrl("profiles/89/feed-v3/profile.webp")
                .build();
        CheerPost firstPost = CheerPost.builder()
                .id(411L)
                .author(author)
                .content("first lightweight")
                .postType(PostType.NORMAL)
                .build();
        CheerPost secondPost = CheerPost.builder()
                .id(412L)
                .author(author)
                .content("second lightweight")
                .postType(PostType.NORMAL)
                .build();
        CountingDirectExecutorService countingExecutor = new CountingDirectExecutorService();
        feedService.setFeedEnrichmentExecutorForTest(countingExecutor);

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), pageable, 2));
        when(imageService.getPostImageUrlsByPostIds(List.of(411L, 412L))).thenReturn(Collections.emptyMap());
        when(profileImageService.getProfileImageUrlForCheerFeed(
                89L,
                "profiles/89/profile.webp",
                "profiles/89/feed-v3/profile.webp"))
                .thenReturn("https://cdn.example/light-profile.webp");
        when(postDtoMapper.toPostLightweightSummaryRes(
                any(CheerPost.class),
                anyList(),
                argThat(profileMap -> "https://cdn.example/light-profile.webp".equals(profileMap.get(89L)))))
                .thenAnswer(invocation -> {
                    CheerPost post = invocation.getArgument(0);
                    return PostLightweightSummaryRes.of(
                            post.getId(),
                            post.getContent(),
                            null,
                            0,
                            0,
                            null,
                            "Light Author",
                            "https://cdn.example/light-profile.webp");
                });

        Page<PostLightweightSummaryRes> page = feedService.listLightweight(null, null, pageable, null);

        assertThat(page.getContent()).extracting(PostLightweightSummaryRes::id).containsExactly(411L, 412L);
        assertThat(countingExecutor.executeCount()).isEqualTo(2);
        verify(profileImageService, times(1)).getProfileImageUrlForCheerFeed(
                89L,
                "profiles/89/profile.webp",
                "profiles/89/feed-v3/profile.webp");
        verify(metricsService).recordFeedRequest(
                eq("feed_lightweight"),
                eq(false),
                isNull(),
                isNull(),
                eq(false),
                eq(2),
                eq("success"),
                longThat(value -> value >= 0));
    }

    @Test
    @DisplayName("listLightweight returns fallback when the enrichment executor rejects work")
    void listLightweight_returnsFallbackWhenEnrichmentExecutorRejectsWork() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().name("Rejected Author").build();
        CheerPost post = CheerPost.builder()
                .id(420L)
                .author(author)
                .content("rejected lightweight")
                .postType(PostType.NORMAL)
                .build();
        CountingDirectExecutorService rejectedExecutor = new CountingDirectExecutorService();
        rejectedExecutor.shutdown();
        feedService.setFeedEnrichmentExecutorForTest(rejectedExecutor);

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(postDtoMapper.toPostLightweightSummaryRes(eq(post), eq(List.of()), anyMap()))
                .thenReturn(PostLightweightSummaryRes.of(
                        420L,
                        "rejected lightweight",
                        null,
                        0,
                        0,
                        null,
                        "Rejected Author",
                        null));

        Page<PostLightweightSummaryRes> response =
                feedService.listLightweight(null, null, pageable, null);

        assertThat(response.getContent()).extracting(PostLightweightSummaryRes::id).containsExactly(420L);
        verify(metricsService).recordFeedEnrichment("busy");
        verify(imageService, never()).getPostImageUrlsByPostIds(anyList());
    }

    @Test
    @DisplayName("listLightweight records dependency fallback as enrichment failure")
    void listLightweight_recordsDependencyFallbackAsEnrichmentFailure() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().id(91L).name("Fallback Author").build();
        CheerPost post = CheerPost.builder()
                .id(422L)
                .author(author)
                .content("dependency fallback")
                .postType(PostType.NORMAL)
                .build();
        feedService.setFeedEnrichmentExecutorForTest(new CountingDirectExecutorService());

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(imageService.getPostImageUrlsByPostIds(List.of(422L)))
                .thenThrow(new IllegalStateException("image storage unavailable"));
        when(postDtoMapper.toPostLightweightSummaryRes(eq(post), eq(List.of()), anyMap()))
                .thenReturn(PostLightweightSummaryRes.of(
                        422L,
                        "dependency fallback",
                        null,
                        0,
                        0,
                        null,
                        "Fallback Author",
                        null));

        Page<PostLightweightSummaryRes> response =
                feedService.listLightweight(null, null, pageable, null);

        assertThat(response.getContent()).extracting(PostLightweightSummaryRes::id).containsExactly(422L);
        verify(metricsService).recordFeedEnrichment("failure");
        verify(metricsService).recordFeedEnrichment("success");
        verify(metricsService, never()).recordFeedEnrichment("timeout");
        verify(metricsService, never()).recordFeedEnrichment("busy");
    }

    @Test
    @DisplayName("listLightweight records profile fallback as enrichment failure")
    void listLightweight_recordsProfileFallbackAsEnrichmentFailure() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder()
                .id(92L)
                .name("Profile Fallback Author")
                .profileImageUrl("https://cdn.example/profile-fallback.webp")
                .build();
        CheerPost post = CheerPost.builder()
                .id(423L)
                .author(author)
                .content("profile dependency fallback")
                .postType(PostType.NORMAL)
                .build();
        feedService.setFeedEnrichmentExecutorForTest(new CountingDirectExecutorService());

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(imageService.getPostImageUrlsByPostIds(List.of(423L))).thenReturn(Collections.emptyMap());
        when(profileImageService.getProfileImageUrlForCheerFeed(
                92L,
                "https://cdn.example/profile-fallback.webp",
                null))
                .thenThrow(new IllegalStateException("profile storage unavailable"));
        when(postDtoMapper.toPostLightweightSummaryRes(
                eq(post),
                eq(List.of()),
                argThat(profileMap -> "https://cdn.example/profile-fallback.webp".equals(profileMap.get(92L)))))
                .thenReturn(PostLightweightSummaryRes.of(
                        423L,
                        "profile dependency fallback",
                        null,
                        0,
                        0,
                        null,
                        "Profile Fallback Author",
                        "https://cdn.example/profile-fallback.webp"));

        Page<PostLightweightSummaryRes> response =
                feedService.listLightweight(null, null, pageable, null);

        assertThat(response.getContent()).extracting(PostLightweightSummaryRes::id).containsExactly(423L);
        assertThat(response.getContent().getFirst().authorProfileImage())
                .isEqualTo("https://cdn.example/profile-fallback.webp");
        verify(metricsService).recordFeedEnrichment("failure");
        verify(metricsService).recordFeedEnrichment("success");
        verify(metricsService, never()).recordFeedEnrichment("timeout");
        verify(metricsService, never()).recordFeedEnrichment("busy");
    }

    @Test
    @DisplayName("enrichment returns busy fallback when all bulkhead permits are occupied")
    void supplyEnrichmentAsync_returnsBusyFallbackWhenBulkheadIsSaturated() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondExecuted = new AtomicBoolean();
        org.springframework.test.util.ReflectionTestUtils.setField(feedService, "feedEnrichmentMaxConcurrency", 1);
        org.springframework.test.util.ReflectionTestUtils.setField(feedService, "feedEnrichmentPermitWaitTimeoutMs", 25L);
        org.springframework.test.util.ReflectionTestUtils.setField(feedService, "feedEnrichmentTaskTimeoutMs", 1000L);

        CompletableFuture<String> first = invokeEnrichmentAsync(feedService, () -> {
            firstStarted.countDown();
            try {
                releaseFirst.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return "first";
        }, "first-fallback");

        try {
            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> second = invokeEnrichmentAsync(feedService, () -> {
                secondExecuted.set(true);
                return "second";
            }, "second-fallback");

            assertThat(second.get(500, TimeUnit.MILLISECONDS)).isEqualTo("second-fallback");
            assertThat(secondExecuted).isFalse();
            verify(metricsService).recordFeedEnrichment("busy");
        } finally {
            releaseFirst.countDown();
        }

        assertThat(first.get(500, TimeUnit.MILLISECONDS)).isEqualTo("first");
        verify(metricsService).recordFeedEnrichment("success");
        assertThat(awaitActiveFeedEnrichmentCount(feedService)).isZero();
    }

    @Test
    @DisplayName("enrichment returns failure fallback when the supplier fails")
    void supplyEnrichmentAsync_returnsFailureFallbackWhenSupplierFails() throws Exception {
        CompletableFuture<String> response = invokeEnrichmentAsync(
                feedService,
                () -> {
                    throw new IllegalStateException("supplier failed");
                },
                "failure-fallback");

        assertThat(response.get(500, TimeUnit.MILLISECONDS)).isEqualTo("failure-fallback");
        verify(metricsService).recordFeedEnrichment("failure");
        assertThat(awaitActiveFeedEnrichmentCount(feedService)).isZero();
    }

    @Test
    @DisplayName("listLightweight returns fallback when enrichment exceeds its bounded timeout")
    void listLightweight_returnsFallbackWhenEnrichmentExceedsBoundedTimeout() throws Exception {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().id(90L).name("Slow Author").build();
        CheerPost post = CheerPost.builder()
                .id(421L)
                .author(author)
                .content("slow lightweight")
                .postType(PostType.NORMAL)
                .build();
        CountDownLatch imageStarted = new CountDownLatch(1);
        CountDownLatch imageInterrupted = new CountDownLatch(1);
        CountDownLatch releaseImage = new CountDownLatch(1);
        ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        org.springframework.test.util.ReflectionTestUtils.setField(feedService, "feedEnrichmentMaxConcurrency", 1);

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(imageService.getPostImageUrlsByPostIds(List.of(421L))).thenAnswer(invocation -> {
            imageStarted.countDown();
            try {
                releaseImage.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                imageInterrupted.countDown();
                throw ex;
            }
            return Collections.emptyMap();
        });
        when(postDtoMapper.toPostLightweightSummaryRes(eq(post), eq(List.of()), anyMap()))
                .thenReturn(PostLightweightSummaryRes.of(
                        421L,
                        "slow lightweight",
                        null,
                        0,
                        0,
                        null,
                        "Slow Author",
                        null));

        Future<Page<PostLightweightSummaryRes>> responseFuture =
                requestExecutor.submit(() -> feedService.listLightweight(null, null, pageable, null));
        try {
            assertThat(imageStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Page<PostLightweightSummaryRes> response = responseFuture.get(1500, TimeUnit.MILLISECONDS);
            assertThat(response.getContent()).extracting(PostLightweightSummaryRes::id).containsExactly(421L);
            verify(metricsService).recordFeedEnrichment("timeout");
            assertThat(imageInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitActiveFeedEnrichmentCount(feedService)).isZero();
        } finally {
            releaseImage.countDown();
            requestExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("team-scoped anonymous list records failure feed metric")
    void list_teamScopedAnonymousRecordsFailureMetric() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> feedService.list("LG", "NOTICE", pageable, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(metricsService).recordFeedRequest(
                eq("feed"),
                eq(true),
                eq("NOTICE"),
                isNull(),
                eq(false),
                eq(20),
                eq("failure"),
                longThat(value -> value >= 0));
        verify(postRepo, never()).findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(Pageable.class));
    }
}
