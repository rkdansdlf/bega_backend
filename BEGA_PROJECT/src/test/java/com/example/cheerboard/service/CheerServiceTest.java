package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private com.example.common.service.AIModerationService moderationService;

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
}
