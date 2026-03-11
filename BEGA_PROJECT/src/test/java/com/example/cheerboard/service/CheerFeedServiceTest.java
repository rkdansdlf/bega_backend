package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
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

    @BeforeEach
    void setUp() {
        lenient().when(publicVisibilityVerifier.canAccess(any(), any())).thenReturn(true);
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
