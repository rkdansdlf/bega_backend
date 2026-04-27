package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.service.UserService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostChangesResponse;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
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
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheerFeedServiceTest {

    @InjectMocks
    private CheerFeedService feedService;

    @Mock
    private CheerPostRepo postRepo;
    @Mock
    private RedisPostService redisPostService;
    @Mock
    private ImageService imageService;
    @Mock
    private CheerBookmarkRepo bookmarkRepo;
    @Mock
    private CheerInteractionService interactionService; // FeedService uses InteractionService
    @Mock
    private PostDtoMapper postDtoMapper;
    @Mock
    private PublicVisibilityVerifier publicVisibilityVerifier;
    @Mock
    private UserService userService;
    @Mock
    private FollowService followService;
    @Mock
    private BlockService blockService;
    @Mock
    private PermissionValidator permissionValidator;
    @Mock
    private PopularFeedScoringService popularFeedScoringService;

    @BeforeEach
    void setUp() {
        lenient().when(publicVisibilityVerifier.canAccess(any(), any())).thenReturn(true);
        lenient().when(popularFeedScoringService.isHotEligible(any(CheerPost.class), anyInt(), any())).thenReturn(true);
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
        when(redisPostService.getCachedHotStatuses(anyCollection())).thenReturn(Collections.emptyMap());
        when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());

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
    }

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
    @DisplayName("checkPostChanges counts only visible new posts")
    void checkPostChanges_countsOnlyVisiblePosts() {
        UserEntity visibleAuthor = UserEntity.builder().id(101L).name("Visible").build();
        UserEntity hiddenAuthor = UserEntity.builder().id(202L).name("Hidden").privateAccount(true).build();
        CheerPost visiblePost = CheerPost.builder().id(12L).author(visibleAuthor).postType(PostType.NORMAL).build();
        CheerPost hiddenPost = CheerPost.builder().id(13L).author(hiddenAuthor).postType(PostType.NORMAL).build();

        when(postRepo.findNewPostsSinceOrderByIdDesc(10L, null)).thenReturn(List.of(hiddenPost, visiblePost));
        when(publicVisibilityVerifier.canAccess(visibleAuthor, null)).thenReturn(true);
        when(publicVisibilityVerifier.canAccess(hiddenAuthor, null)).thenReturn(false);

        PostChangesResponse response = feedService.checkPostChanges(10L, null, null);

        assertThat(response.newCount()).isEqualTo(1);
        assertThat(response.latestId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("checkPostChanges treats all teamId as public filter")
    void checkPostChanges_allTeamIdBehavesAsPublicFilter() {
        when(postRepo.findNewPostsSinceOrderByIdDesc(10L, null)).thenReturn(List.of());

        PostChangesResponse response = feedService.checkPostChanges(10L, "all", null);

        assertThat(response.newCount()).isEqualTo(0);
        assertThat(response.latestId()).isEqualTo(10L);
        verify(permissionValidator, never()).validateTeamAccess(any(), anyString(), anyString());
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
        when(redisPostService.getCachedHotStatuses(anyCollection())).thenReturn(Collections.emptyMap());
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
        when(redisPostService.getCachedHotStatuses(anyCollection())).thenReturn(Collections.emptyMap());
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

    private CheerPost createSimplePost(Long postId, Long authorId) {
        UserEntity author = UserEntity.builder().id(authorId).name("Author-" + authorId).build();
        return CheerPost.builder()
                .id(postId)
                .author(author)
                .content("Post-" + postId)
                .postType(PostType.NORMAL)
                .build();
    }
}
