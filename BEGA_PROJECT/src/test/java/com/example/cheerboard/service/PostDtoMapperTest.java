package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
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
    @DisplayName("prefetch HOT 캐시가 stale false여도 현재 계산값으로 응답과 캐시를 갱신한다")
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
        verify(redisPostService).cacheHotStatus(post.getId(), true);
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
                .postType(PostType.NORMAL)
                .build();
    }
}
