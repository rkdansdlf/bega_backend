package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.common.service.AIModerationService;
import com.example.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheerCommentService tests")
class CheerCommentServiceTest {

    @Mock
    private CheerCommentRepo commentRepo;

    @Mock
    private CheerCommentLikeRepo commentLikeRepo;

    @Mock
    private CheerPostRepo postRepo;

    @Mock
    private CheerPostService postService;

    @Mock
    private UserRepository userRepo;

    @Mock
    private NotificationService notificationService;

    @Mock
    private BlockService blockService;

    @Mock
    private PublicVisibilityVerifier publicVisibilityVerifier;

    @Mock
    private PermissionValidator permissionValidator;

    @Mock
    private AIModerationService moderationService;

    @Mock
    private CommentDtoMapper commentDtoMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private CheerCommentService cheerCommentService;

    @Test
    @DisplayName("댓글 조회는 최상위 댓글 페이지만 조회하고 페이지 내부 댓글 ID만 좋아요 조회에 사용한다")
    void listComments_usesPagedTopLevelQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        UserEntity viewer = user(7L, "@viewer", "Viewer");
        CheerPost post = CheerPost.builder()
                .id(99L)
                .author(user(1L, "@author", "Author"))
                .build();

        CheerComment reply = CheerComment.builder()
                .id(11L)
                .author(user(3L, "@reply", "Reply"))
                .content("reply")
                .createdAt(Instant.parse("2026-03-25T00:00:01Z"))
                .replies(List.of())
                .build();

        CheerComment root = CheerComment.builder()
                .id(10L)
                .author(user(2L, "@root", "Root"))
                .content("root")
                .createdAt(Instant.parse("2026-03-25T00:00:00Z"))
                .replies(List.of(reply))
                .build();

        Page<CheerComment> page = new PageImpl<>(List.of(root), pageable, 1);
        CommentRes mapped = new CommentRes(
                10L,
                "Root",
                "LG",
                null,
                "@root",
                "root",
                Instant.parse("2026-03-25T00:00:00Z"),
                0,
                false,
                List.of());

        when(postService.findPostById(99L)).thenReturn(post);
        doNothing().when(publicVisibilityVerifier).validate(post.getAuthor(), viewer.getId(), "댓글");
        when(commentRepo.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(99L, pageable)).thenReturn(page);
        when(commentLikeRepo.findLikedCommentIdsByUserIdAndCommentIdIn(7L, List.of(10L, 11L)))
                .thenReturn(List.of(11L));
        when(commentDtoMapper.toCommentRes(root, Set.of(11L))).thenReturn(mapped);

        Page<CommentRes> result = cheerCommentService.listComments(99L, pageable, viewer);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        verify(commentRepo).findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(99L, pageable);
        verify(commentRepo, never()).findCommentsWithRepliesByPostId(99L);
        verify(commentLikeRepo).findLikedCommentIdsByUserIdAndCommentIdIn(7L, List.of(10L, 11L));
        verify(commentDtoMapper).toCommentRes(root, Set.of(11L));
    }

    @Test
    @DisplayName("댓글 삭제는 post proxy 대신 재조회한 게시글로 count와 hot score를 갱신한다")
    void deleteComment_refetchesPostBeforeUpdatingCounts() {
        UserEntity actor = user(7L, "@actor", "Actor");
        UserEntity author = user(8L, "@author", "Author");

        CheerPost referencedPost = CheerPost.builder()
                .id(41L)
                .author(author)
                .commentCount(1)
                .build();

        CheerPost managedPost = CheerPost.builder()
                .id(41L)
                .author(author)
                .commentCount(1)
                .build();

        CheerComment comment = CheerComment.builder()
                .id(21L)
                .post(referencedPost)
                .author(author)
                .content("comment")
                .build();

        when(commentRepo.findById(21L)).thenReturn(java.util.Optional.of(comment));
        when(userRepo.findByIdForWrite(7L)).thenReturn(java.util.Optional.of(actor));
        when(userRepo.lockUsableAuthorForWrite(7L)).thenReturn(java.util.Optional.of(7L));
        when(postService.findPostById(41L)).thenReturn(managedPost);
        when(commentRepo.countByPostId(41L)).thenReturn(0L);

        cheerCommentService.deleteComment(21L, actor);

        verify(permissionValidator).validateOwnerOrAdmin(actor, author, "댓글 삭제");
        verify(commentRepo).delete(comment);
        verify(commentRepo).countByPostId(41L);
        verify(postRepo).setExactCommentCount(41L, 0);
        verify(postService).findPostById(41L);
        verify(postService).updateHotScore(managedPost);
        assertThat(managedPost.getCommentCount()).isEqualTo(0);
    }

    private static UserEntity user(Long id, String handle, String name) {
        return UserEntity.builder()
                .id(id)
                .email(handle + "@test.com")
                .handle(handle)
                .name(name)
                .role("ROLE_USER")
                .build();
    }
}
