package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.cheerboard.config.CurrentUser; // Unused in service tests usually if user passed in
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.kbo.entity.TeamEntity;
import com.example.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheerPostServiceTest {

    @InjectMocks
    private CheerPostService postService;

    @Mock
    private CheerPostRepo postRepo;

    @Mock
    private CheerPostRepostRepo repostRepo;

    @Mock
    private BlockService blockService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepo;

    @Mock
    private com.example.cheerboard.storage.service.ImageService imageService;

    @Mock
    private com.example.kbo.repository.TeamRepository teamRepo;

    @Mock
    private PermissionValidator permissionValidator;

    @Mock
    private com.example.common.service.AIModerationService moderationService;

    @Mock
    private PostDtoMapper postDtoMapper;

    @Mock
    private RedisPostService redisPostService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PopularFeedScoringService popularFeedScoringService;

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
        postService.updateHotScore(post);

        // Then
        verify(redisPostService).updateHotScore(postId, 7.5, PopularFeedAlgorithm.TIME_DECAY);
        verify(redisPostService).updateHotScore(postId, 0.42, PopularFeedAlgorithm.ENGAGEMENT_RATE);
    }

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
                .postType(PostType.NORMAL)
                .build();

        mockWriteEnabledAuthor(me);
        when(postRepo.findById(postId)).thenReturn(Optional.of(original));
        when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(false);

        when(postRepo.findByAuthorAndRepostOfAndRepostType(me, original, CheerPost.RepostType.SIMPLE))
                .thenReturn(Optional.empty()); // No existing repost

        when(postRepo.save(any(CheerPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RepostToggleResponse response = postService.toggleRepost(postId, me);

        // Then
        assertThat(response.reposted()).isTrue();
        assertThat(response.count()).isEqualTo(1);

        verify(postRepo, times(2)).save(any(CheerPost.class));
        verify(notificationService).createNotification(
                eq(author.getId()),
                any(),
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

        mockWriteEnabledAuthor(me);
        when(postRepo.findById(postId)).thenReturn(Optional.of(original));
        when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(true);

        // When & Then
        assertThrows(IllegalStateException.class, () -> postService.toggleRepost(postId, me));

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

        mockWriteEnabledAuthor(me);
        when(postRepo.findById(postId)).thenReturn(Optional.of(original));
        when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(false);

        // When & Then
        assertThrows(IllegalStateException.class, () -> postService.toggleRepost(postId, me));

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

        mockWriteEnabledAuthor(me);
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

        when(postRepo.saveAndFlush(any(CheerPost.class))).thenReturn(savedPost);
        when(postDtoMapper.toNewPostDetailRes(any(CheerPost.class), any(UserEntity.class)))
                .thenReturn(PostDetailRes.of(
                        1L, "LG", "LG", "LG", "#C30452", "My Content", "Me", 100L,
                        "me", "me@example.com", "http://example.com/me.jpg", null,
                        0, 0, 0, false, false, false, null, 0, 0, false, "CHEER"));

        // When
        PostDetailRes res = postService.createPost(req, me);

        // Then
        assertThat(res.content()).isEqualTo("My Content");
        org.mockito.ArgumentCaptor<CheerPost> postCaptor = org.mockito.ArgumentCaptor.forClass(CheerPost.class);
        verify(postRepo).saveAndFlush(postCaptor.capture());
        CheerPost capturedPost = postCaptor.getValue();
        assertThat(capturedPost.getContent()).isEqualTo("My Content");
    }

    @Test
    @DisplayName("Update Post Entity Success - Title Change")
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

        when(postRepo.findById(postId)).thenReturn(Optional.of(existing));
        doNothing().when(permissionValidator).validateOwnerOrAdmin(any(), any(), any());
        when(moderationService.checkContent(any()))
                .thenReturn(com.example.common.service.AIModerationService.ModerationResult.allow());

        mockWriteEnabledAuthor(me); // updatePostEntity calls resolveWriteAuthor

        // When
        CheerPost updated = postService.updatePostEntity(postId, req, me);

        // Then
        assertThat(updated.getContent()).isEqualTo("New Content");
    }

    private void mockWriteEnabledAuthor(UserEntity me) {
        when(userRepo.findByIdForWrite(me.getId())).thenReturn(Optional.of(me));
        when(userRepo.lockUsableAuthorForWrite(me.getId())).thenReturn(Optional.of(me.getId()));
        lenient().when(userRepo.lockUsableAuthorForWriteWithTokenVersion(eq(me.getId()), anyInt()))
                .thenReturn(Optional.of(me.getId()));
        doNothing().when(entityManager).refresh(me);
    }
}
