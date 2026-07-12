package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.TeamEntity;
import com.example.profile.storage.service.ProfileImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostDtoMapperTest {

    @Mock
    private HotPostChecker hotPostChecker;

    @Mock
    private ImageService imageService;

    @Mock
    private RedisPostService redisPostService;

    @Mock
    private ProfileImageService profileImageService;

    @Test
    @DisplayName("prefetch 요약 매핑은 현재 HOT 계산값을 쓰되 Redis write-back을 하지 않는다")
    void toPostSummaryRes_prefetchedHotStatusUsesComputedValue() {
        PostDtoMapper mapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService, profileImageService);
        CheerPost post = createPost(41L, 3);

        when(hotPostChecker.isHotPost(post, 5)).thenReturn(true);

        PostSummaryRes result = mapper.toPostSummaryRes(
                post,
                false,
                false,
                false,
                false,
                0,
                Collections.emptyList(),
                Map.of(post.getId(), 2),
                Map.of(post.getId(), false),
                Collections.emptyMap());

        assertThat(result.isHot()).isTrue();
        assertThat(result.views()).isEqualTo(5);
        verify(redisPostService, never()).cacheHotStatus(post.getId(), true);
    }

    @Test
    @DisplayName("prefetch 요약 매핑은 Redis prefetch map이 null이어도 기본값으로 응답한다")
    void toPostSummaryRes_prefetchedSummaryHandlesNullPrefetchMaps() {
        PostDtoMapper mapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService, profileImageService);
        CheerPost post = createPost(43L, 7);

        when(hotPostChecker.isHotPost(post, 7)).thenReturn(false);

        PostSummaryRes result = mapper.toPostSummaryRes(
                post,
                false,
                false,
                false,
                false,
                0,
                null,
                null,
                null,
                null);

        assertThat(result.isHot()).isFalse();
        assertThat(result.views()).isEqualTo(7);
        assertThat(result.imageUrls()).isEmpty();
        verify(redisPostService, never()).cacheHotStatus(post.getId(), false);
    }

    @Test
    @DisplayName("prefetch 요약 매핑은 미리 로딩된 피드 프로필 URL을 재사용한다")
    void toPostSummaryRes_prefetchedSummaryUsesPreloadedFeedProfileImageUrl() {
        PostDtoMapper mapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService, profileImageService);
        CheerPost post = createPost(44L, 3);

        when(hotPostChecker.isHotPost(post, 3)).thenReturn(false);

        PostSummaryRes result = mapper.toPostSummaryRes(
                post,
                false,
                false,
                false,
                false,
                0,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Map.of(post.getAuthor().getId(), "https://cdn.example/feed-profile.webp"));

        assertThat(result.authorProfileImageUrl()).isEqualTo("https://cdn.example/feed-profile.webp");
        verify(profileImageService, never()).getProfileImageUrlForCheerFeed(anyLong(), any(), any());
    }

    @Test
    @DisplayName("lightweight 요약 매핑은 미리 로딩된 피드 프로필 URL을 재사용한다")
    void toPostLightweightSummaryRes_usesPreloadedFeedProfileImageUrl() {
        PostDtoMapper mapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService, profileImageService);
        CheerPost post = createPost(45L, 3);

        PostLightweightSummaryRes result = mapper.toPostLightweightSummaryRes(
                post,
                List.of("https://cdn.example/post.webp"),
                Map.of(post.getAuthor().getId(), "https://cdn.example/feed-profile.webp"));

        assertThat(result.imageUrl()).isEqualTo("https://cdn.example/post.webp");
        assertThat(result.authorProfileImage()).isEqualTo("https://cdn.example/feed-profile.webp");
        verify(profileImageService, never()).getProfileImageUrlForCheerFeed(anyLong(), any(), any());
    }

    @Test
    @DisplayName("lightweight mapping preserves the entity post type")
    void toPostLightweightSummaryRes_preservesPostType() {
        PostDtoMapper mapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService, profileImageService);
        CheerPost post = createPost(46L, 0, PostType.CHECKIN);

        PostLightweightSummaryRes result = mapper.toPostLightweightSummaryRes(post, Collections.emptyList());

        assertThat(result.postType()).isEqualTo("CHECKIN");
        assertThat(result.linkedContent()).isNull();
    }

    @Test
    @DisplayName("embedded repost mapping preserves the original entity post type")
    void toPostSummaryRes_embeddedPostPreservesPostType() {
        PostDtoMapper mapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService, profileImageService);
        CheerPost original = createPost(47L, 0, PostType.RECRUITMENT);
        CheerPost repost = createPost(48L, 0);
        repost.setRepostOf(original);
        repost.setRepostType(CheerPost.RepostType.QUOTE);

        PostSummaryRes result = mapper.toPostSummaryRes(
                repost,
                false,
                false,
                false,
                false,
                0,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap());

        assertThat(result.originalPost()).isNotNull();
        assertThat(result.originalPost().postType()).isEqualTo("RECRUITMENT");
        assertThat(result.originalPost().linkedContent()).isNull();
    }

    @Test
    @DisplayName("단건 매핑도 stale HOT 캐시보다 현재 계산값을 우선한다")
    void toPostSummaryRes_singleLookupUsesComputedHotStatus() {
        PostDtoMapper mapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService, profileImageService);
        CheerPost post = createPost(42L, 4);

        when(redisPostService.getViewCount(post.getId())).thenReturn(1);
        when(redisPostService.getCachedHotStatus(post.getId())).thenReturn(false);
        when(hotPostChecker.isHotPost(post, 5)).thenReturn(true);

        PostSummaryRes result = mapper.toPostSummaryRes(
                post,
                false,
                false,
                false,
                false,
                0,
                List.of());

        assertThat(result.isHot()).isTrue();
        assertThat(result.views()).isEqualTo(5);
        verify(redisPostService).cacheHotStatus(post.getId(), true);
    }

    private CheerPost createPost(Long postId, int views) {
        return createPost(postId, views, PostType.NORMAL);
    }

    private CheerPost createPost(Long postId, int views, PostType postType) {
        TeamEntity team = TeamEntity.builder()
                .teamId("NC")
                .teamName("NC 다이노스")
                .teamShortName("NC")
                .city("창원")
                .color("#071D3D")
                .build();

        UserEntity author = UserEntity.builder()
                .id(7L)
                .name("Tester")
                .handle("@tester")
                .favoriteTeam(team)
                .profileImageUrl("https://example.com/profile.png")
                .email("tester@example.com")
                .role("ROLE_USER")
                .build();

        return CheerPost.builder()
                .id(postId)
                .team(team)
                .content("content")
                .author(author)
                .views(views)
                .createdAt(Instant.parse("2026-03-19T00:00:00Z"))
                .postType(postType)
                .build();
    }
}
