package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.BlockService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.LinkedPostLookupRes;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.repo.CheerPostRepo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        private PublicVisibilityVerifier publicVisibilityVerifier;
        @Mock
        private PermissionValidator permissionValidator;
        @Mock
        private CheerLinkedPostService linkedPostService;
        @Mock
        private CheerPostRepo postRepo;

        @Test
        @DisplayName("Create Post maps a newly created entity to a 201-capable result")
        void createPost_new_returnsCreatedResult() {
                UserEntity me = UserEntity.builder().id(100L).build();
                CreatePostReq req = new CreatePostReq("LG", "content", null, "NORMAL");
                CheerPost post = CheerPost.builder().id(1L).author(me).postType(PostType.NORMAL).build();
                PostDetailRes detail = mock(PostDetailRes.class);
                when(current.get()).thenReturn(me);
                when(postService.createPost(req, me)).thenReturn(new CheerPostCreationOutcome(post, true));
                when(postDtoMapper.toNewPostDetailRes(post, me)).thenReturn(detail);

                CheerPostCreationResult result = cheerService.createPost(req);

                assertThat(result.created()).isTrue();
                assertThat(result.post()).isSameAs(detail);
        }

        @Test
        @DisplayName("Duplicate recovery reloads after the writer transaction rolls back")
        void createPost_duplicateConflict_returnsRaceWinner() {
                Long diaryId = 41L;
                UserEntity me = UserEntity.builder().id(100L).build();
                CreatePostReq req = new CreatePostReq(
                                "LG", "content", null, "CHECKIN", null,
                                null, null, null, null, null, null, null, diaryId, null);
                CheerPost winner = CheerPost.builder()
                                .id(7L).author(me).postType(PostType.CHECKIN).diaryId(diaryId).build();
                PostDetailRes detail = mock(PostDetailRes.class);
                when(current.get()).thenReturn(me);
                when(postService.createPost(req, me)).thenThrow(new DataIntegrityViolationException("duplicate"));
                when(postRepo.findFirstByDiaryIdAndDeletedFalse(diaryId)).thenReturn(java.util.Optional.of(winner));
                when(postDtoMapper.toNewPostDetailRes(winner, me)).thenReturn(detail);

                CheerPostCreationResult result = cheerService.createPost(req);

                assertThat(result.created()).isFalse();
                assertThat(result.post()).isSameAs(detail);
                verify(postRepo).findFirstByDiaryIdAndDeletedFalse(diaryId);
        }

        @Test
        @DisplayName("Create facade has no outer transaction around writer rollback recovery")
        void createPost_hasNoTransactionalBoundary() throws Exception {
                assertThat(CheerService.class.getMethod("createPost", CreatePostReq.class)
                                .getAnnotation(Transactional.class)).isNull();
                assertThat(CheerPostService.class.getMethod("createPost", CreatePostReq.class, UserEntity.class)
                                .getAnnotation(Transactional.class)).isNotNull();
        }

        @Test
        @DisplayName("Duplicate recovery rethrows when no active linked winner exists")
        void createPost_duplicateConflictWithoutWinner_rethrows() {
                UserEntity me = UserEntity.builder().id(100L).build();
                CreatePostReq req = new CreatePostReq(
                                "LG", "content", null, "RECRUITMENT", null,
                                null, null, null, null, null, null, null, null, 51L);
                DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate");
                when(current.get()).thenReturn(me);
                when(postService.createPost(req, me)).thenThrow(conflict);
                when(postRepo.findFirstByPartyIdAndDeletedFalse(51L)).thenReturn(java.util.Optional.empty());

                assertThatThrownBy(() -> cheerService.createPost(req)).isSameAs(conflict);
        }

        @Test
        @DisplayName("Linked lookup delegates with the authenticated actor")
        void lookupLinkedPost_delegatesWithCurrentUser() {
                UserEntity me = UserEntity.builder().id(100L).build();
                LinkedPostLookupRes expected = mock(LinkedPostLookupRes.class);
                when(current.get()).thenReturn(me);
                when(linkedPostService.lookup(41L, null, me)).thenReturn(expected);

                assertThat(cheerService.lookupLinkedPost(41L, null)).isSameAs(expected);
        }

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
                doNothing().when(publicVisibilityVerifier).validate(author, me.getId(), "게시글");

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
                doThrow(new AccessDeniedException("차단 관계"))
                                .when(publicVisibilityVerifier).validate(author, me.getId(), "게시글");

                // When & Then
                assertThrows(AccessDeniedException.class, () -> cheerService.get(postId));

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

        @Test
        @DisplayName("toggleLike - interactionService.toggleLike 위임 확인")
        void toggleLike_delegatesToInteractionService() {
                UserEntity me = UserEntity.builder().id(100L).build();
                when(current.get()).thenReturn(me);

                cheerService.toggleLike(1L);

                verify(current).get();
                verify(interactionService).toggleLike(1L, me);
        }

        @Test
        @DisplayName("toggleBookmark - interactionService.toggleBookmark 위임 확인")
        void toggleBookmark_delegatesToInteractionService() {
                UserEntity me = UserEntity.builder().id(100L).build();
                when(current.get()).thenReturn(me);

                cheerService.toggleBookmark(1L);

                verify(current).get();
                verify(interactionService).toggleBookmark(1L, me);
        }

        @Test
        @DisplayName("toggleRepost - postService.toggleRepost 위임 확인")
        void toggleRepost_delegatesToPostService() {
                UserEntity me = UserEntity.builder().id(100L).build();
                when(current.get()).thenReturn(me);

                cheerService.toggleRepost(1L);

                verify(current).get();
                verify(postService).toggleRepost(1L, me);
        }

        @Test
        @DisplayName("List - 비로그인 시 null 사용자로 feedService에 위임")
        void list_withUnauthenticatedUser_delegatesWithNull() {
                Pageable pageable = Pageable.unpaged();
                when(current.getOrNull()).thenReturn(null);

                cheerService.list("HH", "NORMAL", pageable);

                verify(feedService).list("HH", "NORMAL", pageable, null);
        }

        @Test
        @DisplayName("Get Post Images - visibility validated before delegation")
        void getPostImages_validatesVisibilityBeforeDelegating() {
                Long postId = 10L;
                UserEntity me = UserEntity.builder().id(100L).build();
                UserEntity author = UserEntity.builder().id(200L).build();
                CheerPost post = CheerPost.builder().id(postId).author(author).build();

                when(current.getOrNull()).thenReturn(me);
                when(postService.findPostById(postId)).thenReturn(post);
                doNothing().when(publicVisibilityVerifier).validate(author, me.getId(), "게시글");

                cheerService.getPostImages(postId);

                verify(publicVisibilityVerifier).validate(author, me.getId(), "게시글");
                verify(postService).getPostImages(postId);
        }

        @Test
        @DisplayName("Get Post Images - inaccessible post is rejected")
        void getPostImages_rejectsInaccessiblePost() {
                Long postId = 10L;
                UserEntity author = UserEntity.builder().id(200L).privateAccount(true).build();
                CheerPost post = CheerPost.builder().id(postId).author(author).build();

                when(current.getOrNull()).thenReturn(null);
                when(postService.findPostById(postId)).thenReturn(post);
                doThrow(new AccessDeniedException("비공개 게시글"))
                                .when(publicVisibilityVerifier).validate(author, null, "게시글");

                assertThrows(AccessDeniedException.class, () -> cheerService.getPostImages(postId));

                verify(postService, never()).getPostImages(postId);
        }
}
