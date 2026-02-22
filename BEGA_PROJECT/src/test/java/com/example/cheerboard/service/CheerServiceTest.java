package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.BlockService;
import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.PostDetailRes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheerServiceTest {

        @InjectMocks
        private CheerService cheerService;

        @Mock
        private CheerPostService postService;
        @Mock
        private CheerInteractionService interactionService;
        @Mock
        private CheerFeedService feedService;
        @Mock
        private CheerCommentService commentService;

        @Mock
        private CurrentUser current;
        @Mock
        private PostDtoMapper postDtoMapper;
        @Mock
        private RedisPostService redisPostService;
        @Mock
        private BlockService blockService;
        @Mock
        private PermissionValidator permissionValidator;

        @Test
        @DisplayName("Get Post - Orchestration Success")
        void getPost_orchestration_success() {
                // Given
                Long postId = 1L;
                UserEntity me = UserEntity.builder().id(100L).build();
                UserEntity author = UserEntity.builder().id(200L).build();
                CheerPost post = CheerPost.builder().id(postId).author(author).build();

                when(current.getOrNull()).thenReturn(me);
                when(postService.findPostById(postId)).thenReturn(post);
                when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(false);

                // Mock interaction service checks called by reconstructPostDetailRes
                when(interactionService.isPostLikedByUser(postId, me.getId())).thenReturn(true);
                when(interactionService.isPostBookmarkedByUser(postId, me.getId())).thenReturn(false);
                when(permissionValidator.isOwnerOrAdmin(me, author)).thenReturn(false);
                when(interactionService.isPostRepostedByUser(postId, me.getId())).thenReturn(false);
                when(interactionService.getBookmarkCount(postId)).thenReturn(5);

                when(postDtoMapper.toPostDetailRes(
                                eq(post), eq(true), eq(false), eq(false), eq(false), eq(5)))
                                .thenReturn(mock(PostDetailRes.class));

                // When
                cheerService.get(postId);

                // Then
                verify(redisPostService).incrementViewCount(postId, me.getId());
                verify(postDtoMapper).toPostDetailRes(eq(post), eq(true), eq(false), eq(false), eq(false), eq(5));
        }

        @Test
        @DisplayName("Get Post - Blocked Exception")
        void getPost_blocked() {
                // Given
                Long postId = 1L;
                UserEntity me = UserEntity.builder().id(100L).build();
                UserEntity author = UserEntity.builder().id(200L).build();
                CheerPost post = CheerPost.builder().id(postId).author(author).build();

                when(current.getOrNull()).thenReturn(me);
                when(postService.findPostById(postId)).thenReturn(post);
                when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(true);

                // When & Then
                assertThrows(IllegalStateException.class, () -> cheerService.get(postId));

                verify(redisPostService, never()).incrementViewCount(anyLong(), any());
        }

        @Test
        @DisplayName("List - Delegation")
        void list_delegation() {
                // Given
                Pageable pageable = Pageable.unpaged();
                UserEntity me = UserEntity.builder().id(100L).build();
                when(current.getOrNull()).thenReturn(me);

                // When
                cheerService.list("LG", "NORMAL", pageable);

                // Then
                verify(feedService).list("LG", "NORMAL", pageable, me);
        }
}
