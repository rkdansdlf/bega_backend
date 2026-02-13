package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.kbo.entity.TeamEntity;
import com.example.notification.service.NotificationService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheerServiceTest {

        @InjectMocks
        private CheerService cheerService;

        @Mock
        private CheerPostRepo postRepo;

        @Mock
        private CheerPostRepostRepo repostRepo;

        @Mock
        private BlockService blockService;

        @Mock
        private CurrentUser current;

        @Mock
        private NotificationService notificationService;

        @Mock
        private UserRepository userRepo;

        @Mock
        private com.example.cheerboard.repo.CheerCommentRepo commentRepo;
        @Mock
        private com.example.cheerboard.repo.CheerPostLikeRepo likeRepo;
        @Mock
        private com.example.cheerboard.repo.CheerCommentLikeRepo commentLikeRepo;
        @Mock
        private com.example.cheerboard.repo.CheerBookmarkRepo bookmarkRepo;
        @Mock
        private com.example.cheerboard.repo.CheerReportRepo reportRepo;
        @Mock
        private com.example.kbo.repository.TeamRepository teamRepo;
        @Mock
        private com.example.cheerboard.storage.service.ImageService imageService;
        @Mock
        private FollowService followService;
        @Mock
        private PermissionValidator permissionValidator;
        @Mock
        private PostDtoMapper postDtoMapper;
        @Mock
        private RedisPostService redisPostService;
        @Mock
        private PopularFeedScoringService popularFeedScoringService;
        @Mock
        private com.example.common.service.AIModerationService moderationService;
        @Mock
        private com.example.profile.storage.service.ProfileImageService profileImageService;

        @Test
        @DisplayName("Repost Success - Toggle On")
        void toggleRepost_success_on() {
                // Given
                Long postId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity author = UserEntity.builder().id(200L).name("Author").build();
                CheerPost original = CheerPost.builder()
                                .id(postId)
                                .author(author)
                                .content("Original Post")
                                .postType(PostType.NORMAL) // Corrected enum
                                .build();

                when(current.get()).thenReturn(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(original));
                when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(false);

                when(postRepo.findByAuthorAndRepostOfAndRepostType(me, original, CheerPost.RepostType.SIMPLE))
                                .thenReturn(Optional.empty()); // No existing repost

                when(postRepo.save(any(CheerPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                RepostToggleResponse response = cheerService.toggleRepost(postId);

                // Then
                assertThat(response.reposted()).isTrue(); // Corrected accessor
                assertThat(response.count()).isEqualTo(1); // Original count 0 -> 1

                verify(postRepo, times(2)).save(any(CheerPost.class));
                // Verify notification with correct args: targetUserId, type, title, message,
                // relatedId
                verify(notificationService).createNotification(
                                eq(author.getId()),
                                any(), // enum
                                anyString(),
                                anyString(),
                                eq(postId));
        }

        @Test
        @DisplayName("Repost Failed - Toggle Blocked User")
        void toggleRepost_failed_blocked() {
                // Given
                Long postId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity author = UserEntity.builder().id(200L).name("Author").build();
                CheerPost original = CheerPost.builder().id(postId).author(author).build();

                when(current.get()).thenReturn(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(original));
                when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(true);

                // When & Then
                assertThrows(IllegalStateException.class, () -> cheerService.toggleRepost(postId));

                verify(postRepo, never()).save(any(CheerPost.class));
        }

        @Test
        @DisplayName("Repost Failed - Private Account")
        void toggleRepost_failed_private() {
                // Given
                Long postId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity author = UserEntity.builder().id(200L).privateAccount(true).name("PrivateAuthor").build();
                CheerPost original = CheerPost.builder().id(postId).author(author).build();

                when(current.get()).thenReturn(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(original));
                // Not blocked
                when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(false);

                // When & Then
                assertThrows(IllegalStateException.class, () -> cheerService.toggleRepost(postId));

                verify(postRepo, never()).save(any(CheerPost.class));
        }

        @Test
        @DisplayName("Create Post Success - With Title")
        void createPost_success_withTitle() {
                // Given
                Long userId = 100L;
                TeamEntity team = TeamEntity.builder().teamId("LG").teamName("LG").build();
                UserEntity me = UserEntity.builder().id(userId).name("Me").favoriteTeam(team).build();
                CreatePostReq req = new CreatePostReq("LG", "My Content", null, "CHEER");

                when(current.get()).thenReturn(me);
                when(teamRepo.findById("LG")).thenReturn(Optional.of(team));
                doNothing().when(permissionValidator).validateTeamAccess(any(), any(), any());
                when(moderationService.checkContent(any()))
                                .thenReturn(com.example.common.service.AIModerationService.ModerationResult.allow());

                CheerPost savedPost = CheerPost.builder()
                                .id(1L)
                                .author(me)
                                .content("My Content")
                                .team(team)
                                .build();

                when(postRepo.save(any(CheerPost.class))).thenReturn(savedPost);
                when(postDtoMapper.toNewPostDetailRes(any(CheerPost.class), any(UserEntity.class)))
                                .thenReturn(PostDetailRes.of(
                                                1L, "LG", "LG", "LG", "#C30452", "My Content", "Me", 100L,
                                                "me", "me@example.com", "http://example.com/me.jpg", null,
                                                0, 0, 0, false, false, false, null, 0, 0, false, "CHEER"));

                // When
                PostDetailRes res = cheerService.createPost(req);

                // Then
                assertThat(res.content()).isEqualTo("My Content");
                org.mockito.ArgumentCaptor<CheerPost> postCaptor = org.mockito.ArgumentCaptor.forClass(CheerPost.class);
                verify(postRepo).save(postCaptor.capture());
                CheerPost capturedPost = postCaptor.getValue();
                assertThat(capturedPost.getContent()).isEqualTo("My Content");
        }

        @Test
        @DisplayName("Update Post Success - Title Change")
        void updatePost_success_titleChange() {
                // Given
                Long postId = 1L;
                Long userId = 100L;
                UserEntity me = UserEntity.builder().id(userId).name("Me").build();
                CheerPost existing = CheerPost.builder()
                                .id(postId)
                                .author(me)
                                .content("Old Content")
                                .build();
                UpdatePostReq req = new UpdatePostReq("New Content");

                when(current.get()).thenReturn(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(existing));
                doNothing().when(permissionValidator).validateOwnerOrAdmin(any(), any(), any());
                when(moderationService.checkContent(any()))
                                .thenReturn(com.example.common.service.AIModerationService.ModerationResult.allow());

                when(postDtoMapper.toPostDetailRes(any(CheerPost.class), anyBoolean(), anyBoolean(), anyBoolean(),
                                anyBoolean(), anyInt())).thenAnswer(inv -> {
                                        CheerPost p = inv.getArgument(0);
                                        return PostDetailRes.of(
                                                        p.getId(), "LG", "LG", "LG", "#000",
                                                        p.getContent(), "Me", userId, "me", "me@example.com",
                                                        "http://example.com/me.jpg",
                                                        null,
                                                        0, 0, 0, false, false, false, null, 0, 0, false, "CHEER");
                                });

                // When
                PostDetailRes res = cheerService.updatePost(postId, req);

                // Then
                assertThat(res.content()).isEqualTo("New Content");
        }

        @Test
        @DisplayName("Toggle Like Success")
        void toggleLike_success() {
                // Given
                Long postId = 1L;
                Long userId = 100L;
                UserEntity me = UserEntity.builder().id(userId).name("Me").build();
                CheerPost post = CheerPost.builder().id(postId).author(me).build(); // Author is me for simplicity

                when(current.get()).thenReturn(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(post));
                when(userRepo.findById(userId)).thenReturn(Optional.of(me));

                // Case: Not liked yet -> Like
                when(likeRepo.existsById(any(CheerPostLike.Id.class))).thenReturn(false);

                // When
                LikeToggleResponse res = cheerService.toggleLike(postId);

                // Then
                assertThat(res.liked()).isTrue();
                assertThat(res.likes()).isEqualTo(1);
                verify(likeRepo).save(any(CheerPostLike.class));

                // Case: Liked -> Unlike
                CheerPostLike existingLike = new CheerPostLike();
                existingLike.setPost(post);
                existingLike.setUser(me);
                when(likeRepo.existsById(any(CheerPostLike.Id.class))).thenReturn(true);

                // When
                res = cheerService.toggleLike(postId);

                // Then
                assertThat(res.liked()).isFalse();
                assertThat(res.likes()).isEqualTo(0);
                verify(likeRepo).deleteById(any(CheerPostLike.Id.class));
        }

        @Test
        @DisplayName("HOT 점수 업데이트 시 TIME_DECAY와 ENGAGEMENT_RATE를 모두 갱신한다")
        void updateHotScore_formula_validation() {
                // Given
                Long postId = 10L;
                UserEntity author = UserEntity.builder().id(200L).name("Author").build();
                CheerPost post = CheerPost.builder()
                                .id(postId)
                                .author(author)
                                .likeCount(4)
                                .commentCount(2)
                                .views(7)
                                .build();

                when(redisPostService.getViewCount(postId)).thenReturn(3);
                when(popularFeedScoringService.calculateTimeDecayScore(eq(post), eq(10), any()))
                                .thenReturn(7.5);
                when(popularFeedScoringService.calculateEngagementRateScore(eq(post), eq(10)))
                                .thenReturn(0.42);

                // When
                cheerService.updateHotScore(post);

                // Then
                verify(redisPostService).updateHotScore(postId, 7.5, PopularFeedAlgorithm.TIME_DECAY);
                verify(redisPostService).updateHotScore(postId, 0.42, PopularFeedAlgorithm.ENGAGEMENT_RATE);
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
                when(postRepo.findAllByIdWithGraph(hotIds)).thenReturn(List.of(post1, post2, post3));
                when(current.getOrNull()).thenReturn(null);
                when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());
                when(bookmarkRepo.countByPostIds(anyList())).thenReturn(Collections.emptyList());
                when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
                when(redisPostService.getCachedHotStatuses(anyCollection())).thenReturn(Collections.emptyMap());
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
                Page<PostSummaryRes> page = cheerService.getHotPosts(pageable, "TIME_DECAY");

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
