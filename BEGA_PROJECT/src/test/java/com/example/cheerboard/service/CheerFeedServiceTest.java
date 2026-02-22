package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
                            post.getAuthor().getId(),
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
