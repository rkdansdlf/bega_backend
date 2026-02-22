package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerCommentLike;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostBookmark;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.CheerPostRepost;
import com.example.cheerboard.dto.BookmarkResponse;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.dto.ReportCaseRes;
import com.example.cheerboard.dto.ReportRequest;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.UserNotFoundException;
import com.example.notification.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerInteractionService {

    private final CheerPostLikeRepo likeRepo;
    private final CheerBookmarkRepo bookmarkRepo;
    private final CheerCommentLikeRepo commentLikeRepo;
    private final CheerReportRepo reportRepo;
    private final CheerPostRepo postRepo;
    private final CheerCommentRepo commentRepo;
    private final CheerPostRepostRepo repostRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final BlockService blockService;
    private final PermissionValidator permissionValidator;
    private final EntityManager entityManager;

    // We need to trigger hot score updates
    private final CheerPostService postService; // for updateHotScore

    @Transactional
    public LikeToggleResponse toggleLike(Long postId, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerPost post = postService.findPostById(postId);

        // [NEW] 차단 관계 확인 (양방향)
        validateNoBlockBetween(author.getId(), post.getAuthor().getId(), "좋아요를 누를 수 없습니다.");

        CheerPostLike.Id likeId = new CheerPostLike.Id(post.getId(), author.getId());

        boolean liked;
        int likes;

        try {
            UserEntity postAuthor = userRepo.findByIdForWrite(Objects.requireNonNull(post.getAuthor().getId()))
                    .orElseThrow(() -> new UserNotFoundException(post.getAuthor().getId()));

            if (likeRepo.existsById(likeId)) {
                // 좋아요 취소
                likeRepo.deleteById(likeId);
                likes = Math.max(0, post.getLikeCount() - 1);
                post.setLikeCount(likes);
                liked = false;

                // 작성자 포인트 차감 (Entity Update)
                postAuthor.deductCheerPoints(1);
                userRepo.save(postAuthor);
                log.info("Points deducted for user {}: -1 (Entity Update)", postAuthor.getId());

            } else {
                // 좋아요 추가
                CheerPostLike like = new CheerPostLike();
                like.setId(likeId);
                like.setPost(post);
                like.setUser(author);
                likeRepo.save(like);
                likes = post.getLikeCount() + 1;
                post.setLikeCount(likes);
                liked = true;

                // 작성자 포인트 증가 (Entity Update)
                postAuthor.addCheerPoints(1);
                userRepo.save(postAuthor);
                log.info("Points awarded to user {}: +1 (Entity Update)", postAuthor.getId());

                // 게시글 작성자에게 알림 (본인이 아닐 때만)
                if (!postAuthor.getId().equals(author.getId())) {
                    boolean isBlocked = blockService.hasBidirectionalBlock(author.getId(), postAuthor.getId());
                    if (!isBlocked) {
                        try {
                            String authorName = author.getName() != null && !author.getName().isBlank()
                                    ? author.getName()
                                    : author.getEmail();

                            notificationService.createNotification(
                                    Objects.requireNonNull(post.getAuthor().getId()),
                                    com.example.notification.entity.Notification.NotificationType.POST_LIKE,
                                    "좋아요 알림",
                                    authorName + "님이 회원님의 게시글을 좋아합니다.",
                                    post.getId());
                        } catch (Exception e) {
                            log.warn("좋아요 알림 생성 실패: postId={}, error={}", post.getId(), e.getMessage());
                        }
                    }
                }
            }
            postRepo.save(Objects.requireNonNull(post));
            postService.updateHotScore(post);
            return Objects.requireNonNull(new LikeToggleResponse(liked, likes));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    @Transactional
    public BookmarkResponse toggleBookmark(Long postId, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerPost target = resolveActionTargetPost(postId);
        CheerPostBookmark.Id bookmarkId = new CheerPostBookmark.Id(target.getId(), author.getId());

        boolean bookmarked;
        try {
            if (bookmarkRepo.existsById(bookmarkId)) {
                bookmarkRepo.deleteById(bookmarkId);
                bookmarked = false;
            } else {
                CheerPostBookmark bookmark = new CheerPostBookmark();
                bookmark.setId(bookmarkId);
                bookmark.setPost(target);
                bookmark.setUser(author);
                bookmarkRepo.save(Objects.requireNonNull(bookmark));
                bookmarked = true;
            }
            int count = Math.toIntExact(bookmarkRepo.countById_PostId(target.getId()));
            return Objects.requireNonNull(new BookmarkResponse(bookmarked, count));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    @Transactional
    public ReportCaseRes reportPost(Long postId, ReportRequest req, UserEntity me) {
        UserEntity reporter = resolveWriteAuthor(me);
        CheerPost post = postService.findPostById(postId);

        if (req.reason() == null) {
            throw new IllegalArgumentException("신고 사유를 선택해 주세요.");
        }

        LocalDateTime dedupeSince = LocalDateTime.now().minusDays(7);
        boolean alreadyReported = reportRepo.existsByPost_IdAndReporter_IdAndCreatedAtAfter(
                postId,
                reporter.getId(),
                dedupeSince);
        if (alreadyReported) {
            throw new IllegalStateException("동일 게시물 신고는 7일 내 1회만 접수할 수 있습니다.");
        }

        long reportCountIn24Hours = reportRepo.countByReporter_IdAndCreatedAtAfter(
                reporter.getId(),
                LocalDateTime.now().minusHours(24));
        if (reportCountIn24Hours >= 20) {
            log.warn("Potential abusive reporting detected. reporterId={}, count24h={}", reporter.getId(),
                    reportCountIn24Hours);
        }

        CheerPostReport report = CheerPostReport.builder()
                .post(post)
                .reporter(reporter)
                .reason(req.reason())
                .description(buildReportDescription(req))
                .evidenceUrl(req.evidenceUrl())
                .requestedAction(req.requestedAction())
                .appealStatus(CheerPostReport.AppealStatus.NONE)
                .build();

        try {
            CheerPostReport saved = reportRepo.save(Objects.requireNonNull(report));
            return Objects.requireNonNull(new ReportCaseRes(
                    saved.getId(),
                    saved.getStatus().name(),
                    saved.getHandledAt(),
                    "관리자 검토 대기",
                    "신고가 정상 접수되었습니다."));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(reporter);
            }
            throw ex;
        }
    }

    @Transactional
    public LikeToggleResponse toggleCommentLike(Long commentId, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerComment comment = findCommentById(commentId);

        permissionValidator.validateTeamAccess(author, comment.getPost().getTeamId(), "댓글 좋아요");

        CheerCommentLike.Id likeId = new CheerCommentLike.Id(comment.getId(), author.getId());

        boolean liked;
        int likes;

        try {
            if (commentLikeRepo.existsById(likeId)) {
                commentLikeRepo.deleteById(likeId);
                likes = Math.max(0, comment.getLikeCount() - 1);
                comment.setLikeCount(likes);
                liked = false;

                UserEntity commentAuthor = userRepo.findById(Objects.requireNonNull(comment.getAuthor().getId()))
                        .orElseThrow(() -> new UserNotFoundException(comment.getAuthor().getId()));
                commentAuthor.deductCheerPoints(1);
                userRepo.save(commentAuthor);
                log.info("Points deducted for comment author {}: -1 (Entity Update)", commentAuthor.getId());

            } else {
                CheerCommentLike like = new CheerCommentLike();
                like.setId(likeId);
                like.setComment(comment);
                like.setUser(author);
                commentLikeRepo.save(like);
                likes = comment.getLikeCount() + 1;
                comment.setLikeCount(likes);
                liked = true;

                UserEntity commentAuthor = userRepo.findById(Objects.requireNonNull(comment.getAuthor().getId()))
                        .orElseThrow(() -> new UserNotFoundException(comment.getAuthor().getId()));
                commentAuthor.addCheerPoints(1);
                userRepo.save(commentAuthor);
                log.info("Points awarded to comment author {}: +1 (Entity Update)", commentAuthor.getId());
            }

            commentRepo.save(Objects.requireNonNull(comment));
            return Objects.requireNonNull(new LikeToggleResponse(liked, likes));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    // --- Read operations for interactions ---

    public boolean isPostLikedByUser(Long postId, Long userId) {
        return likeRepo.existsById(new CheerPostLike.Id(postId, userId));
    }

    public boolean isPostBookmarkedByUser(Long postId, Long userId) {
        return bookmarkRepo.existsById(new CheerPostBookmark.Id(postId, userId));
    }

    public boolean isPostRepostedByUser(Long postId, Long userId) {
        return repostRepo.existsById(new CheerPostRepost.Id(postId, userId));
    }

    public int getBookmarkCount(Long postId) {
        return Math.toIntExact(bookmarkRepo.countById_PostId(postId));
    }

    public java.util.Map<Long, Integer> getBookmarkCountMap(java.util.List<Long> postIds) {
        if (postIds.isEmpty())
            return java.util.Collections.emptyMap();
        return bookmarkRepo.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        CheerBookmarkRepo.PostBookmarkCount::getPostId,
                        item -> item.getBookmarkCount().intValue()));
    }

    public java.util.Set<Long> getLikedPostIds(Long userId, java.util.List<Long> postIds) {
        if (postIds.isEmpty())
            return java.util.Collections.emptySet();
        return likeRepo.findByUserIdAndPostIdIn(userId, postIds).stream()
                .map(l -> l.getId().getPostId())
                .collect(Collectors.toSet());
    }

    public java.util.Set<Long> getBookmarkedPostIds(Long userId, java.util.List<Long> postIds) {
        if (postIds.isEmpty())
            return java.util.Collections.emptySet();
        return bookmarkRepo.findByUserIdAndPostIdIn(userId, postIds).stream()
                .map(b -> b.getId().getPostId())
                .collect(Collectors.toSet());
    }

    public java.util.Set<Long> getRepostedPostIds(Long userId, java.util.List<Long> postIds) {
        if (postIds.isEmpty())
            return java.util.Collections.emptySet();
        return repostRepo.findByUserIdAndPostIdIn(userId, postIds).stream()
                .map(r -> r.getId().getPostId())
                .collect(Collectors.toSet());
    }

    public boolean isCommentLikedByUser(Long commentId, Long userId) {
        return commentLikeRepo.existsById(new CheerCommentLike.Id(commentId, userId));
    }

    public java.util.Set<Long> getLikedCommentIds(Long userId, java.util.List<Long> commentIds) {
        if (commentIds.isEmpty())
            return java.util.Collections.emptySet();
        return commentLikeRepo.findLikedCommentIdsByUserIdAndCommentIdIn(userId, commentIds).stream()
                .collect(Collectors.toSet());
    }

    // --- Helpers (Duplicated to avoid circular dependency on facade, or should be
    // in utility?) ---
    // Ideally put into a shared AuthHelper service. For now duplicating private
    // helpers.

    private CheerComment findCommentById(Long commentId) {
        return commentRepo.findById(Objects.requireNonNull(commentId))
                .orElseThrow(() -> new java.util.NoSuchElementException("댓글을 찾을 수 없습니다: " + commentId));
    }

    private CheerPost resolveActionTargetPost(Long postId) {
        CheerPost target = postService.findPostById(postId);
        return resolveActionTargetPost(target);
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
        // Same logic as CheerPostService
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
        // Same logic as CheerPostService (omitted for brevity, but should be identical)
        // Ideally extract to AuthService
        // For this refactor, I will copy-paste to ensure correctness without creating
        // new common class yet

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
        boolean hasAuthorColumn = lower.contains("author_id") || lower.contains("user_id")
                || lower.contains("reporter_id");
        return hasAuthorColumn;
    }

    private String buildReportDescription(ReportRequest req) {
        StringBuilder description = new StringBuilder();
        if (req.description() != null && !req.description().isBlank()) {
            description.append(req.description().trim());
        } else if (req.requestedReason() != null && !req.requestedReason().isBlank()) {
            description.append(req.requestedReason().trim());
        }

        appendReportMeta(description, "sourceUrl", req.sourceUrl());
        appendReportMeta(description, "license", req.license());
        appendReportMeta(description, "ownerContact", req.ownerContact());
        appendReportMeta(description, "hasRightEvidence",
                req.hasRightEvidence() == null ? null : req.hasRightEvidence().toString());
        appendReportMeta(description, "requestedReason", req.requestedReason());

        String result = description.toString().trim();
        return Objects.requireNonNull(result.isBlank() ? "상세 사유 없음" : result);
    }

    private void appendReportMeta(StringBuilder builder, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append('[').append(key).append("] ").append(value.trim());
    }
}
