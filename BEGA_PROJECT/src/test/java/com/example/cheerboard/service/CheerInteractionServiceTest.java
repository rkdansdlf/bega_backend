package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheerInteractionServiceTest {

    @InjectMocks
    private CheerInteractionService interactionService;

    @Mock
    private CheerPostLikeRepo likeRepo;
    @Mock
    private CheerBookmarkRepo bookmarkRepo;
    @Mock
    private CheerCommentLikeRepo commentLikeRepo;
    @Mock
    private CheerReportRepo reportRepo;
    @Mock
    private CheerPostRepo postRepo;
    @Mock
    private CheerCommentRepo commentRepo;
    @Mock
    private CheerPostRepostRepo repostRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BlockService blockService;
    @Mock
    private PermissionValidator permissionValidator;
    @Mock
    private EntityManager entityManager;
    @Mock
    private CheerPostService postService;

    @Test
    @DisplayName("Toggle Like Success")
    void toggleLike_success() {
        // Given
        Long postId = 1L;
        Long userId = 100L;
        UserEntity me = UserEntity.builder().id(userId).name("Me").build();
        UserEntity author = UserEntity.builder().id(200L).name("Author").build();
        CheerPost post = CheerPost.builder().id(postId).author(author).likeCount(0).build();

        mockWriteEnabledAuthor(me);
        when(postService.findPostById(postId)).thenReturn(post);
        when(blockService.hasBidirectionalBlock(userId, author.getId())).thenReturn(false);
        when(userRepo.findByIdForWrite(author.getId())).thenReturn(Optional.of(author));

        // Case: Not liked yet -> Like
        when(likeRepo.existsById(any(CheerPostLike.Id.class))).thenReturn(false);

        // When
        LikeToggleResponse res = interactionService.toggleLike(postId, me);

        // Then
        assertThat(res.liked()).isTrue();
        assertThat(res.likes()).isEqualTo(1);
        verify(likeRepo).save(any(CheerPostLike.class));
        verify(postService).updateHotScore(post);

        // Case: Liked -> Unlike
        // Reset mocks roughly or just verified flow 1.
        // For strictness, let's create a separate test or reset content.
        // Re-mock for unlike scenario
        post.setLikeCount(1);
        when(likeRepo.existsById(any(CheerPostLike.Id.class))).thenReturn(true);

        // When
        res = interactionService.toggleLike(postId, me);

        // Then
        assertThat(res.liked()).isFalse();
        assertThat(res.likes()).isEqualTo(0);
        verify(likeRepo).deleteById(any(CheerPostLike.Id.class));
    }

    private void mockWriteEnabledAuthor(UserEntity me) {
        when(userRepo.findByIdForWrite(me.getId())).thenReturn(Optional.of(me));
        when(userRepo.lockUsableAuthorForWrite(me.getId())).thenReturn(Optional.of(me.getId()));
        lenient().when(userRepo.lockUsableAuthorForWriteWithTokenVersion(eq(me.getId()), anyInt()))
                .thenReturn(Optional.of(me.getId()));
        doNothing().when(entityManager).refresh(me);
    }
}
