package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.exception.DuplicateCommentException;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.common.service.AIModerationService;
import com.example.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.example.cheerboard.service.CheerServiceConstants.DUPLICATE_COMMENT_ERROR;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerCommentService {

    private static final Duration COMMENT_DUPLICATE_TTL = Duration.ofSeconds(3);
    private static final String COMMENT_DUPLICATE_GUARD_PREFIX = "cheer:comment:dedupe:";

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
    private final PublicVisibilityVerifier publicVisibilityVerifier;
    private final PermissionValidator permissionValidator;
    private final AIModerationService moderationService;
    private final CommentDtoMapper commentDtoMapper;
    private final EntityManager entityManager;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(readOnly = true)
    public Page<CommentRes> listComments(Long postId, Pageable pageable, UserEntity me) {
        // me can be null
        CheerPost post = postService.findPostById(postId);
        CheerPost targetPost = CheerRepostTargetResolver.resolveActionTargetPost(post);
        publicVisibilityVerifier.validate(targetPost.getAuthor(), me != null ? me.getId() : null, "댓글");

        // [NEW] 차단 유저 등 필터링 필요시 추가 - CheerService logic didn't explicitly filter
        // listComments by block,
        // but client side might hide or we should. For now keeping as is.

        Page<CheerComment> commentPage = commentRepo.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(
                Objects.requireNonNull(postId), pageable);
        List<CheerComment> pagedComments = commentPage.getContent();

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

        return new PageImpl<>(Objects.requireNonNull(mapped), pageable, commentPage.getTotalElements());
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
        UserEntity author = CheerAuthorWriteGuard.resolveWriteAuthor(me, userRepo, entityManager);
        CheerPost post = postService.findPostById(postId);
        CheerPost targetPost = CheerRepostTargetResolver.resolveActionTargetPost(post);

        publicVisibilityVerifier.validate(targetPost.getAuthor(), author.getId(), "게시글");
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

            // 댓글 수 원자적 증가 (Race Condition 방지)
            postRepo.incrementCommentCount(targetPost.getId());
            // hot score 계산을 위해 in-memory 엔티티도 동기화 (DB 추가 쿼리 없이)
            targetPost.setCommentCount(targetPost.getCommentCount() + 1);

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
                CheerAuthorWriteGuard.ensureAuthorRecordStillExists(author, userRepo);
            }
            throw ex;
        }
    }

    @Transactional
    public CommentRes addReply(Long postId, Long parentCommentId, CreateCommentReq req, UserEntity me) {
        UserEntity author = CheerAuthorWriteGuard.resolveWriteAuthor(me, userRepo, entityManager);
        CheerPost post = postService.findPostById(postId);
        CheerPost targetPost = CheerRepostTargetResolver.resolveActionTargetPost(post);
        CheerComment parentComment = findCommentById(parentCommentId);

        publicVisibilityVerifier.validate(targetPost.getAuthor(), author.getId(), "게시글");
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

            // 댓글 수 원자적 증가 (Race Condition 방지)
            postRepo.incrementCommentCount(targetPost.getId());
            // hot score 계산을 위해 in-memory 엔티티도 동기화 (DB 추가 쿼리 없이)
            targetPost.setCommentCount(targetPost.getCommentCount() + 1);

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
                CheerAuthorWriteGuard.ensureAuthorRecordStillExists(author, userRepo);
            }
            throw ex;
        }
    }

    @Transactional
    public void deleteComment(Long commentId, UserEntity me) {
        UserEntity author = CheerAuthorWriteGuard.resolveWriteAuthor(me, userRepo, entityManager);
        CheerComment comment = findCommentById(commentId);
        permissionValidator.validateOwnerOrAdmin(author, comment.getAuthor(), "댓글 삭제");

        Long postId = Objects.requireNonNull(comment.getPost().getId());
        try {
            commentRepo.delete(comment);

            // 실제 DB에서 댓글 수 재계산 후 원자적 UPDATE (Lost Update 방지)
            Long actualCount = commentRepo.countByPostId(postId);
            int newCount = actualCount != null ? actualCount.intValue() : 0;
            postRepo.setExactCommentCount(postId, newCount);

            CheerPost managedPost = postService.findPostById(postId);
            managedPost.setCommentCount(newCount);
            postService.updateHotScore(managedPost);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                CheerAuthorWriteGuard.ensureAuthorRecordStillExists(author, userRepo);
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
        String normalizedContent = normalizeCommentContent(content);
        if (!acquireDuplicateCommentGuard(postId, authorId, parentCommentId, normalizedContent)) {
            throw new DuplicateCommentException(DUPLICATE_COMMENT_ERROR);
        }

        Instant threeSecondsAgo = Instant.now().minus(COMMENT_DUPLICATE_TTL);
        List<String> recentContents = parentCommentId == null
                ? commentRepo.findRecentTopLevelContentsByPostIdAndAuthorIdAndCreatedAtAfter(
                        postId, authorId, threeSecondsAgo)
                : commentRepo.findRecentReplyContentsByPostIdAndAuthorIdAndParentCommentIdAndCreatedAtAfter(
                        postId, authorId, parentCommentId, threeSecondsAgo);

        boolean isDuplicate = recentContents.stream()
                .map(this::normalizeCommentContent)
                .anyMatch(normalizedContent::equals);

        if (isDuplicate) {
            throw new DuplicateCommentException(DUPLICATE_COMMENT_ERROR);
        }
    }

    private boolean acquireDuplicateCommentGuard(Long postId, Long authorId, Long parentCommentId, String normalizedContent) {
        try {
            String key = COMMENT_DUPLICATE_GUARD_PREFIX + postId + ":" + authorId + ":"
                    + (parentCommentId != null ? parentCommentId : "root") + ":"
                    + DigestUtils.md5DigestAsHex(normalizedContent.getBytes(StandardCharsets.UTF_8));
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", COMMENT_DUPLICATE_TTL);
            return !Boolean.FALSE.equals(acquired);
        } catch (Exception e) {
            log.warn("Comment duplicate guard unavailable. postId={}, authorId={}", postId, authorId, e);
            return true;
        }
    }

    private String normalizeCommentContent(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("\\s+", " ");
    }

    private void validateNoBlockBetween(Long user1, Long user2, String message) {
        if (blockService.hasBidirectionalBlock(user1, user2)) {
            throw new IllegalStateException(message);
        }
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
