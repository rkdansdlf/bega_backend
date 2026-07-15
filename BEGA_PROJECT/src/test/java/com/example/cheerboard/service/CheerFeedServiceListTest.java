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
class CheerFeedServiceListTest extends CheerFeedServiceTestFixture {

    @Test
    @DisplayName("listByUserHandle rejects inaccessible private accounts")
    void listByUserHandle_rejectsInaccessiblePrivateAccounts() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(userService.getPublicUserProfileByHandle("@private", null))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("비공개 계정"));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> feedService.listByUserHandle("@private", pageable, null));
    }

    @Test
    @DisplayName("listMyPosts returns the authenticated user's authored cheer posts")
    void listMyPosts_returnsAuthenticatedUsersPosts() {
        UserEntity me = UserEntity.builder().id(77L).name("Me").build();
        PageRequest pageable = PageRequest.of(0, 10);
        CheerPost post = createSimplePost(31L, 77L);

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(imageService.getPostImageUrlsByPostIds(List.of(31L))).thenReturn(Collections.emptyMap());
        when(redisPostService.getViewCounts(List.of(31L))).thenReturn(Collections.emptyMap());
        when(interactionService.getBookmarkCountMap(List.of(31L))).thenReturn(Collections.emptyMap());
        when(interactionService.getLikedPostIds(77L, List.of(31L))).thenReturn(Collections.emptySet());
        when(interactionService.getBookmarkedPostIds(77L, List.of(31L))).thenReturn(Collections.emptySet());
        when(interactionService.getRepostedPostIds(77L, List.of(31L))).thenReturn(Collections.emptySet());
        when(postDtoMapper.toPostSummaryRes(
                eq(post),
                eq(false),
                eq(false),
                anyBoolean(),
                eq(false),
                eq(0),
                eq(List.of()),
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
                        "내 응원석 글",
                        "Me",
                        "me",
                        null,
                        null,
                        null,
                        0,
                        0,
                        0,
                        false,
                        0,
                        false,
                        false,
                        true,
                        0,
                        false,
                        "NORMAL",
                        List.of()));

        Page<PostSummaryRes> page = feedService.listMyPosts(pageable, me);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(31L);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("listMyPosts returns an empty page when the authenticated user has no authored posts")
    void listMyPosts_returnsEmptyPageWhenUserHasNoPosts() {
        UserEntity me = UserEntity.builder().id(77L).name("Me").build();
        PageRequest pageable = PageRequest.of(0, 10);

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        Page<PostSummaryRes> page = feedService.listMyPosts(pageable, me);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getNumber()).isZero();
    }

    @Test
    @DisplayName("checkPostChanges counts only visible new posts")
    void checkPostChanges_countsOnlyVisiblePosts() {
        UserEntity visibleAuthor = UserEntity.builder().id(101L).name("Visible").build();
        UserEntity hiddenAuthor = UserEntity.builder().id(202L).name("Hidden").privateAccount(true).build();
        CheerPost visiblePost = CheerPost.builder().id(12L).author(visibleAuthor).postType(PostType.NORMAL).build();
        CheerPost hiddenPost = CheerPost.builder().id(13L).author(hiddenAuthor).postType(PostType.NORMAL).build();

        when(postRepo.findNewPostsSinceOrderByIdAsc(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(hiddenPost, visiblePost));
        when(publicVisibilityVerifier.canAccess(visibleAuthor, null)).thenReturn(true);
        when(publicVisibilityVerifier.canAccess(hiddenAuthor, null)).thenReturn(false);

        PostChangesResponse response = feedService.checkPostChanges(10L, null, null);

        assertThat(response.newCount()).isEqualTo(1);
        assertThat(response.latestId()).isEqualTo(13L);
        verify(postRepo).findNewPostsSinceOrderByIdAsc(
                eq(10L),
                argThat(pageable -> pageable.getPageSize() == 200 && pageable.getPageNumber() == 0));
        verify(metricsService).recordPostChangesPolling(eq(false), eq(2), eq(1), longThat(value -> value >= 0), eq("success"));
    }

    @Test
    @DisplayName("checkPostChanges treats all teamId as public filter")
    void checkPostChanges_allTeamIdBehavesAsPublicFilter() {
        when(postRepo.findNewPostsSinceOrderByIdAsc(eq(10L), any(Pageable.class))).thenReturn(List.of());

        PostChangesResponse response = feedService.checkPostChanges(10L, "all", null);

        assertThat(response.newCount()).isEqualTo(0);
        assertThat(response.latestId()).isEqualTo(10L);
        verify(permissionValidator, never()).validateTeamAccess(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("checkPostChanges uses dedicated team polling query")
    void checkPostChanges_usesDedicatedTeamPollingQuery() {
        UserEntity me = UserEntity.builder().id(77L).name("Me").build();
        CheerPost teamPost = CheerPost.builder().id(14L).author(me).postType(PostType.NORMAL).build();

        when(postRepo.findNewTeamPostsSinceOrderByIdAsc(eq(10L), eq("LG"), any(Pageable.class)))
                .thenReturn(List.of(teamPost));
        when(publicVisibilityVerifier.canAccess(me, 77L)).thenReturn(true);

        PostChangesResponse response = feedService.checkPostChanges(10L, "LG", me);

        assertThat(response.newCount()).isEqualTo(1);
        assertThat(response.latestId()).isEqualTo(14L);
        verify(permissionValidator).validateTeamAccess(me, "LG", "게시글 조회");
        verify(postRepo, never()).findNewPostsSinceOrderByIdAsc(anyLong(), any(Pageable.class));
        verify(metricsService).recordPostChangesPolling(eq(true), eq(1), eq(1), longThat(value -> value >= 0), eq("success"));
    }

    @Test
    @DisplayName("checkPostChanges records failure metrics")
    void checkPostChanges_recordsFailureMetrics() {
        assertThatThrownBy(() -> feedService.checkPostChanges(10L, "LG", null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(metricsService).recordPostChangesPolling(eq(true), eq(0), eq(0), longThat(value -> value >= 0), eq("failure"));
    }

    @Test
    @DisplayName("checkPostChanges advances cursor over hidden scanned posts")
    void checkPostChanges_advancesCursorOverHiddenScannedPosts() {
        UserEntity hiddenAuthor = UserEntity.builder().id(202L).name("Hidden").privateAccount(true).build();
        CheerPost hiddenPost = CheerPost.builder().id(13L).author(hiddenAuthor).postType(PostType.NORMAL).build();

        when(postRepo.findNewPostsSinceOrderByIdAsc(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(hiddenPost));
        when(publicVisibilityVerifier.canAccess(hiddenAuthor, null)).thenReturn(false);

        PostChangesResponse response = feedService.checkPostChanges(10L, null, null);

        assertThat(response.newCount()).isZero();
        assertThat(response.latestId()).isEqualTo(13L);
    }

    @Test
    @DisplayName("checkPostChanges scans the oldest bounded chunk before advancing the cursor")
    void checkPostChanges_scansOldestBoundedChunkBeforeAdvancingCursor() throws Exception {
        var publicQuery = CheerPostRepo.class
                .getMethod("findNewPostsSinceOrderByIdAsc", Long.class, Pageable.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class);
        var teamQuery = CheerPostRepo.class
                .getMethod("findNewTeamPostsSinceOrderByIdAsc", Long.class, String.class, Pageable.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class);

        assertThat(publicQuery.value()).containsIgnoringCase("order by p.id asc");
        assertThat(teamQuery.value()).containsIgnoringCase("order by p.id asc");
    }

    @Test
    @DisplayName("list skips orphaned posts whose author reference is missing")
    void list_skipsPostsWithMissingAuthorReference() {
        PageRequest pageable = PageRequest.of(0, 20);

        CheerPost orphanPost = CheerPost.builder()
                .id(169L)
                .content("orphan")
                .postType(PostType.NORMAL)
                .build();
        CheerPost validPost = createSimplePost(222L, 15L);

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(orphanPost, validPost), pageable, 2));
        when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());
        when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
        when(interactionService.getBookmarkCountMap(anyList())).thenReturn(Collections.emptyMap());
        when(postDtoMapper.toPostSummaryRes(
                eq(validPost),
                eq(false),
                eq(false),
                eq(false),
                eq(false),
                eq(0),
                anyList(),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap()))
                .thenReturn(PostSummaryRes.of(
                        validPost.getId(),
                        "LG",
                        "LG 트윈스",
                        "LG",
                        "#C30452",
                        validPost.getContent(),
                        "Author-15",
                        "author-15",
                        null,
                        null,
                        null,
                        validPost.getCommentCount(),
                        validPost.getLikeCount(),
                        0,
                        false,
                        validPost.getViews(),
                        false,
                        false,
                        false,
                        validPost.getRepostCount(),
                        false,
                        "NORMAL",
                        List.of()));

        Page<PostSummaryRes> page = feedService.list(null, null, pageable, null);

        assertThat(page.getContent()).extracting(PostSummaryRes::id).containsExactly(222L);
        verify(metricsService).recordFeedRequest(
                eq("feed"),
                eq(false),
                isNull(),
                isNull(),
                eq(false),
                eq(20),
                eq("success"),
                longThat(value -> value >= 0));
    }

    @Test
    @DisplayName("list preserves repository page metadata without in-memory visibility filtering")
    void list_preserves_repository_pagination_metadata() {
        PageRequest pageable = PageRequest.of(0, 20);
        CheerPost validPost = createSimplePost(300L, 33L);

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(validPost), PageRequest.of(0, 20), 41));
        when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());
        when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
        when(interactionService.getBookmarkCountMap(anyList())).thenReturn(Collections.emptyMap());
        when(postDtoMapper.toPostSummaryRes(
                eq(validPost),
                eq(false),
                eq(false),
                eq(false),
                eq(false),
                eq(0),
                anyList(),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap()))
                .thenReturn(PostSummaryRes.of(
                        validPost.getId(),
                        "LG",
                        "LG 트윈스",
                        "LG",
                        "#C30452",
                        validPost.getContent(),
                        "Author-33",
                        "author-33",
                        null,
                        null,
                        null,
                        validPost.getCommentCount(),
                        validPost.getLikeCount(),
                        0,
                        false,
                        validPost.getViews(),
                        false,
                        false,
                        false,
                        validPost.getRepostCount(),
                        false,
                        "NORMAL",
                        List.of()));

        Page<PostSummaryRes> page = feedService.list(null, null, pageable, null);

        assertThat(page.getTotalElements()).isEqualTo(41);
        verify(publicVisibilityVerifier, never()).canAccess(any(), isNull());
    }

    @Test
    @DisplayName("list는 같은 작성자의 피드 프로필 URL 정규화를 페이지 안에서 한 번만 수행한다")
    void list_deduplicatesFeedProfileImageResolutionByAuthor() {
        PageRequest pageable = PageRequest.of(0, 2);
        UserEntity author = UserEntity.builder()
                .id(88L)
                .name("Author")
                .handle("author")
                .profileImageUrl("profiles/88/profile.webp")
                .profileFeedImageUrl("profiles/88/feed-v3/profile.webp")
                .build();
        CheerPost firstPost = CheerPost.builder()
                .id(401L)
                .author(author)
                .content("first")
                .postType(PostType.NORMAL)
                .build();
        CheerPost secondPost = CheerPost.builder()
                .id(402L)
                .author(author)
                .content("second")
                .postType(PostType.NORMAL)
                .build();

        when(postRepo.findAll(org.mockito.ArgumentMatchers.<Specification<CheerPost>>any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), pageable, 2));
        when(imageService.getPostImageUrlsByPostIds(List.of(401L, 402L))).thenReturn(Collections.emptyMap());
        when(redisPostService.getViewCounts(List.of(401L, 402L))).thenReturn(Collections.emptyMap());
        when(interactionService.getBookmarkCountMap(List.of(401L, 402L))).thenReturn(Collections.emptyMap());
        when(profileImageService.getProfileImageUrlForCheerFeed(
                88L,
                "profiles/88/profile.webp",
                "profiles/88/feed-v3/profile.webp"))
                .thenReturn("https://cdn.example/profile-feed.webp");
        when(postDtoMapper.toPostSummaryRes(
                any(CheerPost.class),
                eq(false),
                eq(false),
                eq(false),
                eq(false),
                eq(0),
                anyList(),
                anyMap(),
                anyMap(),
                anyMap(),
                argThat(profileMap -> "https://cdn.example/profile-feed.webp".equals(profileMap.get(88L)))))
                .thenAnswer(invocation -> {
                    CheerPost post = invocation.getArgument(0);
                    return PostSummaryRes.of(
                            post.getId(),
                            null,
                            null,
                            null,
                            null,
                            post.getContent(),
                            "Author",
                            "author",
                            "https://cdn.example/profile-feed.webp",
                            null,
                            null,
                            0,
                            0,
                            0,
                            false,
                            0,
                            false,
                            false,
                            false,
                            0,
                            false,
                            "NORMAL",
                            List.of());
                });

        Page<PostSummaryRes> page = feedService.list(null, null, pageable, null);

        assertThat(page.getContent()).extracting(PostSummaryRes::id).containsExactly(401L, 402L);
        verify(profileImageService, times(1)).getProfileImageUrlForCheerFeed(
                88L,
                "profiles/88/profile.webp",
                "profiles/88/feed-v3/profile.webp");
    }
}
