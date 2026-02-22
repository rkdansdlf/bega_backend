package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.QuoteRepostReq;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.kbo.entity.TeamEntity;
import com.example.notification.service.NotificationService;
import com.example.common.exception.RepostNotAllowedException;
import com.example.common.exception.RepostSelfNotAllowedException;
import com.example.common.exception.RepostTargetNotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_SELF_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
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
                when(postRepo.findRepostCountById(postId)).thenReturn(1);

                when(postRepo.findByAuthorAndRepostOfAndRepostType(me, original, CheerPost.RepostType.SIMPLE))
                                .thenReturn(Optional.empty()); // No existing repost

                when(postRepo.save(any(CheerPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                RepostToggleResponse response = postService.toggleRepost(postId, me);

                // Then
                assertThat(response.reposted()).isTrue();
                assertThat(response.count()).isEqualTo(1);

                verify(postRepo, times(1)).save(any(CheerPost.class));
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
                RepostNotAllowedException ex = assertThrows(
                                RepostNotAllowedException.class,
                                () -> postService.toggleRepost(postId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_ALLOWED_BLOCKED_CODE);

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
                RepostNotAllowedException ex = assertThrows(
                                RepostNotAllowedException.class,
                                () -> postService.toggleRepost(postId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_CODE);

                verify(postRepo, never()).save(any(CheerPost.class));
        }

        @Test
        @DisplayName("Repost Failed - Self Post")
        void toggleRepost_failed_self() {
                Long postId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                CheerPost original = CheerPost.builder().id(postId).author(me).build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(original));
                // Removed unnecessary blockService mock here

                RepostSelfNotAllowedException ex = assertThrows(
                                RepostSelfNotAllowedException.class,
                                () -> postService.toggleRepost(postId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
                verify(postRepo, never()).save(any(CheerPost.class));
        }

        @Test
        @DisplayName("Repost Toggle - targets root post when repost target is another repost")
        void toggleRepost_targets_root_for_repost_input() {
                Long repostId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity rootAuthor = UserEntity.builder().id(200L).name("Root Author").build();
                UserEntity repostAuthor = UserEntity.builder().id(300L).name("Repost Author").build();

                CheerPost root = CheerPost.builder()
                                .id(10L)
                                .author(rootAuthor)
                                .content("Root")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost repostOfRoot = CheerPost.builder()
                                .id(repostId)
                                .author(repostAuthor)
                                .repostOf(root)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(repostId)).thenReturn(Optional.of(repostOfRoot));
                when(blockService.hasBidirectionalBlock(me.getId(), rootAuthor.getId())).thenReturn(false);
                when(postRepo.findByAuthorAndRepostOfAndRepostType(me, root, CheerPost.RepostType.SIMPLE))
                                .thenReturn(Optional.empty());
                when(postRepo.save(any(CheerPost.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(postRepo.findRepostCountById(root.getId())).thenReturn(1);

                RepostToggleResponse response = postService.toggleRepost(repostId, me);

                assertThat(response.reposted()).isTrue();
                assertThat(response.count()).isEqualTo(1);
                verify(postRepo).findByAuthorAndRepostOfAndRepostType(me, root, CheerPost.RepostType.SIMPLE);
                verify(postRepo).incrementRepostCount(root.getId());
        }

        @Test
        @DisplayName("Repost Toggle - targets root post and rejects self author")
        void toggleRepost_targets_root_for_repost_input_rejects_self_author() {
                Long repostId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity repostAuthor = UserEntity.builder().id(300L).name("Repost Author").build();

                CheerPost root = CheerPost.builder()
                                .id(10L)
                                .author(me)
                                .content("Root")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost repostOfRoot = CheerPost.builder()
                                .id(repostId)
                                .author(repostAuthor)
                                .repostOf(root)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(repostId)).thenReturn(Optional.of(repostOfRoot));

                RepostSelfNotAllowedException ex = assertThrows(
                                RepostSelfNotAllowedException.class,
                                () -> postService.toggleRepost(repostId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
                verify(postRepo, never()).findByAuthorAndRepostOfAndRepostType(any(), any(), any());
                verify(postRepo, never()).save(any(CheerPost.class));
                verify(blockService, never()).hasBidirectionalBlock(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Repost Toggle - rejects repost target that is own post even when root is another user")
        void toggleRepost_targets_own_repost_is_forbidden() {
                Long repostId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity rootAuthor = UserEntity.builder().id(200L).name("Root Author").build();

                CheerPost root = CheerPost.builder()
                                .id(10L)
                                .author(rootAuthor)
                                .content("Root")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost myRepost = CheerPost.builder()
                                .id(repostId)
                                .author(me)
                                .repostOf(root)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(repostId)).thenReturn(Optional.of(myRepost));

                RepostSelfNotAllowedException ex = assertThrows(
                                RepostSelfNotAllowedException.class,
                                () -> postService.toggleRepost(repostId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
                verify(blockService, never()).hasBidirectionalBlock(anyLong(), anyLong());
                verify(postRepo, never()).findByAuthorAndRepostOfAndRepostType(any(), any(), any());
        }

        @Test
        @DisplayName("Repost Toggle - targets root post and rejects blocked target")
        void toggleRepost_targets_root_for_repost_input_rejects_blocked_root_author() {
                Long repostId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity rootAuthor = UserEntity.builder().id(200L).name("Root Author").build();
                UserEntity repostAuthor = UserEntity.builder().id(300L).name("Repost Author").build();

                CheerPost root = CheerPost.builder()
                                .id(10L)
                                .author(rootAuthor)
                                .content("Root")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost repostOfRoot = CheerPost.builder()
                                .id(repostId)
                                .author(repostAuthor)
                                .repostOf(root)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(repostId)).thenReturn(Optional.of(repostOfRoot));
                when(blockService.hasBidirectionalBlock(me.getId(), rootAuthor.getId())).thenReturn(true);

                RepostNotAllowedException ex = assertThrows(
                                RepostNotAllowedException.class,
                                () -> postService.toggleRepost(repostId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_ALLOWED_BLOCKED_CODE);
                verify(postRepo, never()).findByAuthorAndRepostOfAndRepostType(any(), any(), any());
                verify(postRepo, never()).save(any(CheerPost.class));
                verify(blockService, times(1)).hasBidirectionalBlock(me.getId(), rootAuthor.getId());
        }

        @Test
        @DisplayName("Repost Toggle - targets root post and rejects private target")
        void toggleRepost_targets_root_for_repost_input_rejects_private_root_author() {
                Long repostId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity rootAuthor = UserEntity.builder().id(200L).name("Root Author").privateAccount(true).build();
                UserEntity repostAuthor = UserEntity.builder().id(300L).name("Repost Author").build();

                CheerPost root = CheerPost.builder()
                                .id(10L)
                                .author(rootAuthor)
                                .content("Root")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost repostOfRoot = CheerPost.builder()
                                .id(repostId)
                                .author(repostAuthor)
                                .repostOf(root)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(repostId)).thenReturn(Optional.of(repostOfRoot));
                when(blockService.hasBidirectionalBlock(me.getId(), rootAuthor.getId())).thenReturn(false);

                RepostNotAllowedException ex = assertThrows(
                                RepostNotAllowedException.class,
                                () -> postService.toggleRepost(repostId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_CODE);
                verify(postRepo, never()).findByAuthorAndRepostOfAndRepostType(any(), any(), any());
                verify(postRepo, never()).save(any(CheerPost.class));
        }

        @Test
        @DisplayName("Repost Toggle - nested repost target still checks root for policy")
        void toggleRepost_nested_repost_target_checks_root_policy() {
                Long rootId = 10L;
                Long midRepostId = 2L;
                Long targetRepostId = 1L;
                UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity rootAuthor = UserEntity.builder().id(200L).name("Root Author").privateAccount(true).build();
                UserEntity midRepostAuthor = UserEntity.builder().id(300L).name("Mid Repost Author").build();
                UserEntity topRepostAuthor = UserEntity.builder().id(400L).name("Top Repost Author").build();

                CheerPost root = CheerPost.builder()
                                .id(rootId)
                                .author(rootAuthor)
                                .content("Root")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost midRepost = CheerPost.builder()
                                .id(midRepostId)
                                .author(midRepostAuthor)
                                .repostOf(root)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost topRepost = CheerPost.builder()
                                .id(targetRepostId)
                                .author(topRepostAuthor)
                                .repostOf(midRepost)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(targetRepostId)).thenReturn(Optional.of(topRepost));
                when(blockService.hasBidirectionalBlock(me.getId(), rootAuthor.getId())).thenReturn(false);

                RepostNotAllowedException ex = assertThrows(
                                RepostNotAllowedException.class,
                                () -> postService.toggleRepost(targetRepostId, me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_CODE);
                verify(blockService, times(1)).hasBidirectionalBlock(me.getId(), rootAuthor.getId());
                verify(postRepo, never()).findByAuthorAndRepostOfAndRepostType(any(), any(), any());
                verify(postRepo, never()).save(any(CheerPost.class));
        }

    @Test
    @DisplayName("Toggle Repost - duplicate create request returns idempotent state")
    void toggleRepost_duplicate_create_request_returns_idempotent_state() {
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
        when(postRepo.findRepostCountById(postId)).thenReturn(3);
        when(repostRepo.existsByPostIdAndUserId(postId, me.getId())).thenReturn(true);
        CheerPost existingRepost = CheerPost.builder()
                .id(11L)
                .author(me)
                .repostType(CheerPost.RepostType.SIMPLE)
                .repostOf(original)
                .postType(PostType.NORMAL)
                .build();
        AtomicInteger findCall = new AtomicInteger();
        when(postRepo.findByAuthorAndRepostOfAndRepostType(me, original, CheerPost.RepostType.SIMPLE))
                .thenAnswer(invocation -> {
                    if (findCall.getAndIncrement() == 0) {
                        return Optional.empty();
                    }
                    return Optional.of(existingRepost);
                });
        when(postRepo.save(any(CheerPost.class))).thenThrow(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_cheer_post_simple_repost\""));

        RepostToggleResponse response = postService.toggleRepost(postId, me);

        assertThat(response.reposted()).isTrue();
        assertThat(response.count()).isEqualTo(3);
        verify(postRepo, never()).delete(any(CheerPost.class));
        verify(repostRepo).existsByPostIdAndUserId(postId, me.getId());
        verify(postRepo).findByAuthorAndRepostOfAndRepostType(me, original, CheerPost.RepostType.SIMPLE);
    }

    @Test
    @DisplayName("Create Quote Repost - fail when target is a repost")
    void createQuoteRepost_fail_when_target_is_repost() {
        Long postId = 1L;
        UserEntity me = UserEntity.builder().id(100L).name("Me").build();
                UserEntity author = UserEntity.builder().id(300L).name("Author").build();
                UserEntity rootAuthor = UserEntity.builder().id(200L).name("Root Author").build();

                CheerPost root = CheerPost.builder()
                                .id(2L)
                                .author(rootAuthor)
                                .content("Root")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost repost = CheerPost.builder()
                                .id(postId)
                                .author(author)
                                .repostOf(root)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(repost));

                RepostNotAllowedException ex = assertThrows(
                                RepostNotAllowedException.class,
                                () -> postService.createQuoteRepost(postId, new QuoteRepostReq("인용 내용"), me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_QUOTE_NOT_ALLOWED_CODE);
                verify(postRepo, never()).save(any(CheerPost.class));
        }

    @Test
    @DisplayName("Create Quote Repost - fail when target is own post")
    void createQuoteRepost_fail_when_target_is_my_post() {
            Long postId = 1L;
            UserEntity me = UserEntity.builder().id(100L).name("Me").build();
            CheerPost original = CheerPost.builder()
                                .id(postId)
                                .author(me)
                                .content("My Original")
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(me);
                when(postRepo.findById(postId)).thenReturn(Optional.of(original));

                RepostSelfNotAllowedException ex = assertThrows(
                                RepostSelfNotAllowedException.class,
                                () -> postService.createQuoteRepost(postId, new QuoteRepostReq("인용 내용"), me));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_SELF_NOT_ALLOWED_CODE);
                verify(postRepo, never()).save(any(CheerPost.class));
        }

    @Test
    @DisplayName("Create Quote Repost - fail when target is private author")
    void createQuoteRepost_fail_when_target_is_private_author() {
            Long postId = 1L;
            UserEntity me = UserEntity.builder().id(100L).name("Me").build();
            UserEntity author = UserEntity.builder().id(200L).privateAccount(true).name("PrivateAuthor").build();
            CheerPost original = CheerPost.builder()
                            .id(postId)
                            .author(author)
                            .content("Private Post")
                            .postType(PostType.NORMAL)
                            .build();

            mockWriteEnabledAuthor(me);
            when(postRepo.findById(postId)).thenReturn(Optional.of(original));
            when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(false);

            RepostNotAllowedException ex = assertThrows(
                            RepostNotAllowedException.class,
                            () -> postService.createQuoteRepost(postId, new QuoteRepostReq("인용 내용"), me));
            assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_ALLOWED_PRIVATE_CODE);
            verify(postRepo, never()).save(any(CheerPost.class));
    }

    @Test
    @DisplayName("Create Quote Repost - fail when author is blocked")
    void createQuoteRepost_fail_when_author_is_blocked() {
            Long postId = 1L;
            UserEntity me = UserEntity.builder().id(100L).name("Me").build();
            UserEntity author = UserEntity.builder().id(200L).name("Author").build();
            CheerPost original = CheerPost.builder()
                            .id(postId)
                            .author(author)
                            .content("Blocked Author Post")
                            .postType(PostType.NORMAL)
                            .build();

            mockWriteEnabledAuthor(me);
            when(postRepo.findById(postId)).thenReturn(Optional.of(original));
            when(blockService.hasBidirectionalBlock(me.getId(), author.getId())).thenReturn(true);

            RepostNotAllowedException ex = assertThrows(
                            RepostNotAllowedException.class,
                            () -> postService.createQuoteRepost(postId, new QuoteRepostReq("인용 내용"), me));
            assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_ALLOWED_BLOCKED_CODE);
            verify(postRepo, never()).save(any(CheerPost.class));
    }

    @Test
    @DisplayName("Create Quote Repost - fail when target is missing")
    void createQuoteRepost_fail_when_target_not_found() {
            Long postId = 1L;
            UserEntity me = UserEntity.builder().id(100L).name("Me").build();

            mockWriteEnabledAuthor(me);
            when(postRepo.findById(postId)).thenReturn(Optional.empty());

            RepostTargetNotFoundException ex = assertThrows(
                            RepostTargetNotFoundException.class,
                            () -> postService.createQuoteRepost(postId, new QuoteRepostReq("인용 내용"), me));
            assertThat(ex.getErrorCode()).isEqualTo(REPOST_TARGET_NOT_FOUND_CODE);
            verify(postRepo, never()).save(any(CheerPost.class));
    }

    @Test
    @DisplayName("Toggle Repost - fail when target is missing")
    void toggleRepost_fail_when_target_not_found() {
            Long postId = 1L;
            UserEntity me = UserEntity.builder().id(100L).name("Me").build();

            mockWriteEnabledAuthor(me);
            when(postRepo.findById(postId)).thenReturn(Optional.empty());

            RepostTargetNotFoundException ex = assertThrows(
                            RepostTargetNotFoundException.class,
                            () -> postService.toggleRepost(postId, me));
            assertThat(ex.getErrorCode()).isEqualTo(REPOST_TARGET_NOT_FOUND_CODE);
            verify(postRepo, never()).findByAuthorAndRepostOfAndRepostType(any(), any(), any());
            verify(postRepo, never()).save(any(CheerPost.class));
    }

        @Test
        @DisplayName("Cancel Repost - fail when requester is not owner")
        void cancelRepost_fail_when_not_owner() {
            Long repostId = 1L;
            UserEntity owner = UserEntity.builder().id(200L).name("Owner").build();
                UserEntity requester = UserEntity.builder().id(100L).name("Requester").build();
                CheerPost repost = CheerPost.builder()
                                .id(repostId)
                                .author(owner)
                                .repostType(CheerPost.RepostType.SIMPLE)
                                .build();

                mockWriteEnabledAuthor(requester);
                when(postRepo.findById(repostId)).thenReturn(Optional.of(repost));

                RepostSelfNotAllowedException ex = assertThrows(
                                RepostSelfNotAllowedException.class,
                                () -> postService.cancelRepost(repostId, requester));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_CANCEL_NOT_ALLOWED_CODE);
                verify(postRepo, never()).delete(any(CheerPost.class));
        }

        @Test
        @DisplayName("Cancel Repost - fail when target is not repost")
        void cancelRepost_fail_when_target_is_not_repost() {
                Long postId = 1L;
                UserEntity owner = UserEntity.builder().id(100L).name("Owner").build();
                CheerPost original = CheerPost.builder()
                                .id(postId)
                                .author(owner)
                                .content("Original")
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(owner);
                when(postRepo.findById(postId)).thenReturn(Optional.of(original));

                RepostNotAllowedException ex = assertThrows(
                                RepostNotAllowedException.class,
                                () -> postService.cancelRepost(postId, owner));
                assertThat(ex.getErrorCode()).isEqualTo(REPOST_NOT_A_REPOST_CODE);
                verify(postRepo, never()).delete(any(CheerPost.class));
        }

        @Test
        @DisplayName("Cancel Repost - quote repost removes post and decrements count")
        void cancelRepost_quote_repost_decrements_count() {
                Long repostId = 1L;
                Long originalId = 2L;
                UserEntity owner = UserEntity.builder().id(200L).name("Owner").build();
                CheerPost original = CheerPost.builder()
                                .id(originalId)
                                .author(UserEntity.builder().id(300L).name("Author").build())
                                .content("Original Content")
                                .postType(PostType.NORMAL)
                                .build();
                CheerPost repost = CheerPost.builder()
                                .id(repostId)
                                .author(owner)
                                .repostOf(original)
                                .repostType(CheerPost.RepostType.QUOTE)
                                .postType(PostType.NORMAL)
                                .build();

                mockWriteEnabledAuthor(owner);
                when(postRepo.findById(repostId)).thenReturn(Optional.of(repost));
                when(postRepo.findRepostCountById(originalId)).thenReturn(4);

                RepostToggleResponse response = postService.cancelRepost(repostId, owner);

                assertThat(response.reposted()).isFalse();
                assertThat(response.count()).isEqualTo(4);
                verify(postRepo).delete(repost);
                verify(postRepo).decrementRepostCount(originalId);
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
