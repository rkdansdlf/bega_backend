package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.service.AIModerationService;
import com.example.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerCommentService {

    private final CheerCommentRepo commentRepo;
    private final CheerCommentLikeRepo commentLikeRepo;
    private final CheerPostRepo postRepo; // Use Repo directly for simple lookups or Service? Service has findPostById.
    // Using Service is better for consistency but circular dependency might occur
    // if PostService needs CommentService.
    // PostService doesn't seem to need CommentService.
    private final CheerPostService postService;

    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final BlockService blockService;
    private final PermissionValidator permissionValidator;
    private final AIModerationService moderationService;
    private final CommentDtoMapper commentDtoMapper;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<CommentRes> listComments(Long postId, Pageable pageable, UserEntity me) {
        // me can be null
        postService.findPostById(postId);

        // [NEW] 차단 유저 등 필터링 필요시 추가 - CheerService logic didn't explicitly filter
        // listComments by block,
        // but client side might hide or we should. For now keeping as is.

        // Use the optimized query to fetch comments and replies in one go
        List<CheerComment> allComments = commentRepo.findCommentsWithRepliesByPostId(Objects.requireNonNull(postId));

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allComments.size());

        if (start > allComments.size()) {
            return new PageImpl<>(Objects.requireNonNull(List.of()), pageable, allComments.size());
        }

        List<CheerComment> pagedComments = allComments.subList(start, end);

        Set<Long> likedCommentIds = new HashSet<>();
        if (me != null && !pagedComments.isEmpty()) {
            // 모든 댓글 ID 수집 (대댓글 포함)
            List<Long> allCommentIds = collectAllCommentIds(pagedComments);

            // 한 번의 쿼리로 좋아요 여부 확인
            if (!allCommentIds.isEmpty()) {
                likedCommentIds = new HashSet<>(
                        commentLikeRepo.findLikedCommentIdsByUserIdAndCommentIdIn(me.getId(), allCommentIds));
            }
        }

        final Set<Long> finalLikedIds = likedCommentIds;
        List<CommentRes> mapped = pagedComments.stream()
                .map(comment -> commentDtoMapper.toCommentRes(comment, finalLikedIds))
                .toList();

        return new PageImpl<>(Objects.requireNonNull(mapped), pageable, allComments.size());
    }

    private List<Long> collectAllCommentIds(List<CheerComment> comments) {
        List<Long> ids = new java.util.ArrayList<>();
        for (CheerComment comment : comments) {
            ids.add(comment.getId());
            // 대댓글 ID도 수집
            if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
                ids.addAll(collectAllCommentIds(comment.getReplies()));
            }
        }
        return ids;
    }

    @Transactional
    public CommentRes addComment(Long postId, CreateCommentReq req, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerPost post = postService.findPostById(postId);
        // resolveActionTargetPost logic is for Reposts. Comments should be on the
        // target post?
        // CheerService.addComment uses resolveActionTargetPost.
        // But postService.findPostById returns the raw post.
        // We should replicate resolveActionTargetPost logic or call it if exposed.
        // It was private in CheerService.
        // Let's implement it here or expose in PostService?
        // PostService has private resolveActionTargetPost.
        // Logic: if repost, go to repostOf.
        CheerPost targetPost = resolveActionTargetPost(post);

        validateNoBlockBetween(author.getId(), targetPost.getAuthor().getId(), "차단 관계가 있어 댓글을 작성할 수 없습니다.");

        permissionValidator.validateTeamAccess(author, targetPost.getTeamId(), "댓글 작성");

        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            log.warn("Comment moderation blocked. source={}, riskLevel={}, category={}, reason={}",
                    modResult.decisionSource(), modResult.riskLevel(), modResult.category(), modResult.reason());
            throw new IllegalArgumentException("부적절한 내용이 포함되어 댓글을 작성할 수 없습니다.");
        }

        try {
            checkDuplicateComment(targetPost.getId(), author.getId(), req.content(), null);

            CheerComment comment = saveNewComment(targetPost, author, req);

            // Increment comment count - PostService/PostRepo responsibility?
            // CheerService did: post.setCommentCount(...)
            targetPost.setCommentCount(targetPost.getCommentCount() + 1);
            postRepo.save(targetPost); // Save count update

            postService.updateHotScore(targetPost);

            if (!targetPost.getAuthor().getId().equals(author.getId())) {
                boolean isBlocked = blockService.hasBidirectionalBlock(author.getId(), targetPost.getAuthor().getId());
                if (!isBlocked) {
                    try {
                        String authorName = author.getName() != null && !author.getName().isBlank()
                                ? author.getName()
                                : author.getEmail();

                        notificationService.createNotification(
                                Objects.requireNonNull(targetPost.getAuthor().getId()),
                                com.example.notification.entity.Notification.NotificationType.POST_COMMENT,
                                "새 댓글",
                                authorName + "님이 회원님의 게시글에 댓글을 남겼습니다.",
                                targetPost.getId());
                    } catch (Exception e) {
                        log.warn("댓글 알림 생성 실패: postId={}, error={}", targetPost.getId(), e.getMessage());
                    }
                }
            }

            // For return, we need to know if I liked it (obv no for new comment)
            return Objects.requireNonNull(commentDtoMapper.toCommentRes(comment, Collections.emptySet()));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    @Transactional
    public CommentRes addReply(Long postId, Long parentCommentId, CreateCommentReq req, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerPost post = postService.findPostById(postId);
        CheerPost targetPost = resolveActionTargetPost(post);
        CheerComment parentComment = findCommentById(parentCommentId);

        validateNoBlockBetween(author.getId(), targetPost.getAuthor().getId(), "원글 작성자와 차단 관계가 있어 답글을 작성할 수 없습니다.");
        validateNoBlockBetween(author.getId(), parentComment.getAuthor().getId(), "댓글 작성자와 차단 관계가 있어 답글을 작성할 수 없습니다.");

        if (!parentComment.getPost().getId().equals(targetPost.getId())) {
            throw new IllegalArgumentException("부모 댓글이 해당 게시글에 속하지 않습니다.");
        }

        permissionValidator.validateTeamAccess(author, targetPost.getTeamId(), "대댓글 작성");

        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            log.warn("Reply moderation blocked. source={}, riskLevel={}, category={}, reason={}",
                    modResult.decisionSource(), modResult.riskLevel(), modResult.category(), modResult.reason());
            throw new IllegalArgumentException("부적절한 내용이 포함되어 답글을 작성할 수 없습니다.");
        }

        try {
            checkDuplicateComment(targetPost.getId(), author.getId(), req.content(), parentCommentId);

            CheerComment reply = saveNewReply(targetPost, parentComment, author, req);

            targetPost.setCommentCount(targetPost.getCommentCount() + 1);
            postRepo.save(targetPost);

            postService.updateHotScore(targetPost);

            if (!parentComment.getAuthor().getId().equals(author.getId())) {
                try {
                    String authorName = author.getName() != null && !author.getName().isBlank()
                            ? author.getName()
                            : author.getEmail();

                    notificationService.createNotification(
                            Objects.requireNonNull(parentComment.getAuthor().getId()),
                            com.example.notification.entity.Notification.NotificationType.COMMENT_REPLY,
                            "새 대댓글",
                            authorName + "님이 회원님의 댓글에 답글을 남겼습니다.",
                            targetPost.getId());
                } catch (Exception e) {
                    log.warn("대댓글 알림 생성 실패: postId={}, parentCommentId={}, error={}",
                            targetPost.getId(), parentCommentId, e.getMessage());
                }
            }

            return Objects.requireNonNull(commentDtoMapper.toCommentRes(reply, Collections.emptySet()));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    @Transactional
    public void deleteComment(Long commentId, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerComment comment = findCommentById(commentId);
        permissionValidator.validateOwnerOrAdmin(author, comment.getAuthor(), "댓글 삭제");

        CheerPost post = comment.getPost();
        try {
            commentRepo.delete(comment);

            // 실제 DB에서 댓글 수 재계산 (댓글 + 대댓글 모두 포함)
            Long actualCount = commentRepo.countByPostId(Objects.requireNonNull(post.getId()).longValue());
            post.setCommentCount(actualCount != null ? actualCount.intValue() : 0);
            postRepo.save(post);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    private CheerComment findCommentById(Long commentId) {
        return commentRepo.findById(Objects.requireNonNull(commentId))
                .orElseThrow(() -> new java.util.NoSuchElementException("댓글을 찾을 수 없습니다: " + commentId));
    }

    private CheerComment saveNewComment(CheerPost post, UserEntity author, CreateCommentReq req) {
        return commentRepo.save(Objects.requireNonNull(CheerComment.builder()
                .post(post)
                .author(author)
                .content(req.content())
                .build()));
    }

    private CheerComment saveNewReply(CheerPost post, CheerComment parentComment, UserEntity author,
            CreateCommentReq req) {
        return commentRepo.save(Objects.requireNonNull(CheerComment.builder()
                .post(post)
                .parentComment(parentComment)
                .author(author)
                .content(req.content())
                .build()));
    }

    private void checkDuplicateComment(Long postId, Long authorId, String content, Long parentCommentId) {
        java.time.Instant threeSecondsAgo = java.time.Instant.now().minusSeconds(3);
        boolean isDuplicate;

        if (parentCommentId == null) {
            isDuplicate = commentRepo.existsByPostIdAndAuthorIdAndContentAndParentCommentIsNullAndCreatedAtAfter(
                    postId, authorId, content, threeSecondsAgo);
        } else {
            isDuplicate = commentRepo.existsByPostIdAndAuthorIdAndContentAndParentCommentIdAndCreatedAtAfter(
                    postId, authorId, content, parentCommentId, threeSecondsAgo);
        }

        if (isDuplicate) {
            throw new IllegalStateException("중복된 댓글입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private CheerPost resolveActionTargetPost(CheerPost post) {
        CheerPost current = post;
        int hops = 0;
        while (current.isRepost()) {
            CheerPost parent = current.getRepostOf();
            if (parent == null)
                return current;
            current = parent;
            if (++hops > 32)
                throw new IllegalArgumentException("리포스트 대상이 비정상적으로 설정되어 있습니다.");
        }
        return current;
    }

    private void validateNoBlockBetween(Long user1, Long user2, String message) {
        if (blockService.hasBidirectionalBlock(user1, user2)) {
            throw new IllegalStateException(message);
        }
    }

    private UserEntity resolveWriteAuthor(UserEntity me) {
        // Shared logic duplication
        if (me == null || me.getId() == null) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        Long principalUserId = getAuthenticationUserId();
        if (principalUserId != null && !principalUserId.equals(me.getId())) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        UserEntity author = userRepo.findByIdForWrite(me.getId())
                .orElseThrow(() -> new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요."));
        ensureAuthorRecordStillExists(author);
        try {
            entityManager.refresh(author);
        } catch (EntityNotFoundException e) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        return Objects.requireNonNull(author);
    }

    private UserEntity ensureAuthorRecordStillExists(UserEntity author) {
        if (author == null || author.getId() == null) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        Integer tokenVersion = getAuthenticationTokenVersion();
        if (tokenVersion == null) {
            boolean hasUsableAuthor = userRepo.lockUsableAuthorForWrite(author.getId()).isPresent();
            if (!hasUsableAuthor)
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        } else {
            boolean hasUsableAuthor = userRepo.lockUsableAuthorForWriteWithTokenVersion(author.getId(), tokenVersion)
                    .isPresent();
            if (!hasUsableAuthor)
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        UserEntity freshAuthor = userRepo.findByIdForWrite(author.getId())
                .orElseThrow(() -> new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요."));
        if (tokenVersion != null) {
            int currentTokenVersion = freshAuthor.getTokenVersion() == null ? 0 : freshAuthor.getTokenVersion();
            if (currentTokenVersion != tokenVersion)
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        if (!freshAuthor.isEnabled() || !isAccountUsableForWrite(freshAuthor)) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }
        return Objects.requireNonNull(freshAuthor);
    }

    private boolean isAccountUsableForWrite(UserEntity user) {
        if (!user.isLocked())
            return true;
        if (user.getLockExpiresAt() == null)
            return false;
        return user.getLockExpiresAt().isBefore(LocalDateTime.now());
    }

    private Long getAuthenticationUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null)
            return null;
        Object principal = authentication.getPrincipal();
        if (principal == null)
            return null;
        if (principal instanceof Long userId)
            return userId;
        if (principal instanceof String userId) {
            try {
                return Long.valueOf(userId);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getAuthenticationTokenVersion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null)
            return null;
        Object details = authentication.getDetails();
        if (details == null)
            return null;
        if (details instanceof Integer version)
            return version;
        if (details instanceof Long version)
            return version.intValue();
        if (details instanceof Map<?, ?> map) {
            Object val = map.get("tokenVersion");
            if (val instanceof Integer v)
                return v;
        }
        return null;
    }

    private boolean isDeletedAuthorReference(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message == null)
            return false;
        String lower = message.toLowerCase();
        if (!lower.contains("foreign key") || lower.contains("null value in column"))
            return false;
        boolean hasAuthorColumn = lower.contains("author_id") || lower.contains("user_id");
        return hasAuthorColumn;
    }
}
