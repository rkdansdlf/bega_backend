package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostRepost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.QuoteRepostReq;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.cheerboard.storage.dto.PostImageDto;
import com.example.cheerboard.storage.service.ImageService;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.RepostNotAllowedException;
import com.example.common.exception.RepostSelfNotAllowedException;
import com.example.common.exception.RepostTargetNotFoundException;
import com.example.common.service.AIModerationService;
import com.example.kbo.repository.TeamRepository;
import com.example.kbo.util.TeamCodeNormalizer;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import static com.example.cheerboard.service.CheerServiceConstants.GLOBAL_TEAM_ID;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CANCEL_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_BLOCKED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_PRIVATE_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_ALLOWED_SELF_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_NOT_A_REPOST_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_QUOTE_NOT_ALLOWED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_CYCLE_DETECTED_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_ERROR;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_TARGET_NOT_FOUND_CODE;
import static com.example.cheerboard.service.CheerServiceConstants.REPOST_SELF_NOT_ALLOWED_CODE;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerPostService {

    private final CheerPostRepo postRepo;
    private final CheerPostRepostRepo repostRepo;
    private final TeamRepository teamRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final ImageService imageService;
    private final FollowService followService;
    private final BlockService blockService;
    private final PermissionValidator permissionValidator;
    private final PostDtoMapper postDtoMapper;
    private final AIModerationService moderationService;
    private final RedisPostService redisPostService;
    private final PopularFeedScoringService popularFeedScoringService;
    private final EntityManager entityManager;

    @Transactional
    public PostDetailRes createPost(CreatePostReq req, UserEntity me) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(req.teamId());
        log.debug("createPost - requested authorId={} normalizedTeamId={}", me != null ? me.getId() : null,
                normalizedTeamId);

        UserEntity author = resolveWriteAuthor(me);
        // 저장 직전 재검증(토큰/계정 상태 동기화 갱신)
        author = ensureAuthorRecordStillExists(author);

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        validateSharePolicy(
                req.shareMode(),
                req.content(),
                req.sourceUrl(),
                req.sourceLicense());
        flagPotentialSourceSpam(author.getId(), req.sourceUrl());

        permissionValidator.validateTeamAccess(author, normalizedTeamId, "게시글 작성");

        PostType postType = determinePostType(req, author);
        CheerPost post = buildNewPost(req, author, postType, normalizedTeamId);
        CheerPost savedPost;
        try {
            savedPost = postRepo.saveAndFlush(Objects.requireNonNull(post));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                try {
                    ensureAuthorRecordStillExists(author);
                } catch (InvalidAuthorException invalidAuthor) {
                    throw invalidAuthor;
                }
                log.warn(
                        "createPost - foreign key violation on cheer_post insert but author still valid. authorId={}, teamId={}, message={}",
                        author.getId(), normalizedTeamId,
                        ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
                throw ex;
            }
            throw ex;
        }

        // 팔로워들에게 새 글 알림 (notify_new_posts=true 인 팔로워에게만)
        sendNewPostNotificationToFollowers(savedPost, author);

        return postDtoMapper.toNewPostDetailRes(savedPost, author);
    }

    @Transactional
    public PostDetailRes updatePost(Long id, UpdatePostReq req, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(author, post.getAuthor(), "게시글 수정");

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        updatePostContent(post, req);

        // When updating, we need to return the full detail response
        // Note: In the original service, it re-checked likes/bookmarks.
        // For efficiency, we can assume the caller (controller/facade) might handle
        // full re-fetching if needed,
        // OR we can return a basic detail response here.
        // The original code returned a PostDetailRes with current user states.

        // Since this service is "Post CRUD", it might be better to return the updated
        // entity
        // and let the facade handle response construction with user interaction states.
        // However, to keep it simple and match the signature return type:

        // Use default false for interactions, Facade can override or we fetch here.
        // Ideally Facade should call this, get the updated post, and then build the
        // response.
        // But let's follow the implementation plan where this service handles the
        // logic.

        // We will return a DTO assuming no interaction changes during update.
        // But verifying interactions requires Repo access which we don't have (LikeRepo
        // is in InteractionService).
        // For now, let's return a DTO with "false" for interactions and let the Facade
        // enrich it if necessary,
        // or better, change the return type to CheerPost and let Facade map it.
        // The plan says "CheerPostService... Core Post CRUD...".
        // Let's modify the signature to return PostDetailRes to match usage, but we
        // might need to inject LikeRepo?
        // No, we want to separate concerns.
        // Strategy: Return PostDetailRes with known info. The update endpoint usually
        // just needs the updated content.
        // Actually, the original code did:
        // boolean liked = isPostLikedByUser(id, me.getId()); ...

        // To avoid circular dependency or tight coupling, lets inject the Repos here
        // too?
        // Or better: Change the return type to `CheerPost` and let the Facade handle
        // the DTO conversion.
        // But wait, `createPost` returns `PostDetailRes` using locally available info
        // (new post = no likes).
        // `updatePost` needs existing state.

        // Let's look at `CheerService` again. `updatePost` calls `toPostDetailRes`.
        // If I move `updatePost` here, I need to know if the user liked/bookmarked it
        // to return the same format response.
        // If `CheerInteractionService` holds the LikeRepo, `CheerPostService` shouldn't
        // access it directly ideally.
        // Maybe `CheerPostService` should just perform the update and return the
        // `CheerPost`.
        // The Facade then calls `postDtoMapper.toPostDetailRes(post, isLiked,
        // isBookmarked...)`.

        // Decision: `createPost`, `updatePost` will return `CheerPost`. Facade handles
        // DTO conversion.
        // This is cleaner.

        return postDtoMapper.toNewPostDetailRes(post, me); // Fallback for now, Facade will reconstruct if needed.
    }

    // Changing return type to CheerPost to allow Facade to enrich with interaction
    // data
    @Transactional
    public CheerPost updatePostEntity(Long id, UpdatePostReq req, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(author, post.getAuthor(), "게시글 수정");

        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        CheerPost.ShareMode shareModeForValidation = req.shareMode() != null ? req.shareMode() : post.getShareMode();
        validateSharePolicy(
                shareModeForValidation,
                req.content(),
                req.sourceUrl() != null ? req.sourceUrl() : post.getSourceUrl(),
                req.sourceLicense() != null ? req.sourceLicense() : post.getSourceLicense());

        updatePostContent(post, req);
        return post;
    }

    @Transactional
    public void deletePost(Long id, UserEntity me) {
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "게시글 삭제");

        post.setDeleted(true);
        postRepo.save(post);

        boolean storageClean = imageService.deleteImagesByPostId(post.getId());

        if (storageClean) {
            postRepo.delete(post);
        } else {
            log.warn("게시글 삭제 중 일부 이미지 삭제 실패. Soft Delete 상태 유지 및 스케줄러 위임: postId={}", id);
        }
    }

    @Transactional
    public RepostToggleResponse toggleRepost(Long postId, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        try {
            CheerPost original = resolveRepostActionTarget(author, postId, false);

            Optional<CheerPost> existing = postRepo.findByAuthorAndRepostOfAndRepostType(
                    author, original, CheerPost.RepostType.SIMPLE);

            if (existing.isPresent()) {
                postRepo.delete(existing.get());
                repostRepo.deleteByPostIdAndUserId(original.getId(), author.getId());
                postRepo.decrementRepostCount(original.getId());
                updateHotScore(original);
                return new RepostToggleResponse(false, readRepostCount(original.getId()));
            }

            CheerPost repost = CheerPost.builder()
                    .author(author)
                    .team(original.getTeam())
                    .repostOf(original)
                    .repostType(CheerPost.RepostType.SIMPLE)
                    .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                    .content("")
                    .postType(PostType.NORMAL)
                    .build();
            postRepo.save(Objects.requireNonNull(repost));
            createRepostTracking(original, author);
            postRepo.incrementRepostCount(original.getId());
            sendRepostNotification(author, original, "리포스트 알림");
            updateHotScore(original);
            return new RepostToggleResponse(true, readRepostCount(original.getId()));
        } catch (DataIntegrityViolationException ex) {
            if (isRepostDuplicateViolation(ex)) {
                CheerPost original = resolveRepostActionTarget(author, postId, false);
                return getSimpleRepostState(author, original);
            }
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    @Transactional
    public CheerPost createQuoteRepost(Long originalPostId, QuoteRepostReq req, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);
        CheerPost original = resolveRepostActionTarget(author, originalPostId, true);

        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        try {
            CheerPost quoteRepost = CheerPost.builder()
                    .author(author)
                    .team(original.getTeam())
                    .repostOf(original)
                    .repostType(CheerPost.RepostType.QUOTE)
                    .shareMode(CheerPost.ShareMode.INTERNAL_QUOTE)
                    .content(sanitizePostContent(req.content()))
                    .postType(PostType.NORMAL)
                    .build();
            postRepo.save(Objects.requireNonNull(quoteRepost));
            postRepo.incrementRepostCount(original.getId());
            int updatedRepostCount = readRepostCount(original.getId());
            original.setRepostCount(updatedRepostCount);

            updateHotScore(original);
            sendRepostNotification(author, original, "인용 리포스트");

            return quoteRepost;
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    @Transactional
    public RepostToggleResponse cancelRepost(Long repostId, UserEntity me) {
        UserEntity author = resolveWriteAuthor(me);

        try {
            CheerPost repost = findPostById(repostId);
            validateRepostCancelRequest(author, repost);

            CheerPost original = requireRepostTarget(repost);
            postRepo.delete(repost);

            if (original != null && original.getId() != null) {
                if (repost.isSimpleRepost()) {
                    repostRepo.deleteByPostIdAndUserId(original.getId(), author.getId());
                }
                postRepo.decrementRepostCount(original.getId());
            }

            return new RepostToggleResponse(false, original == null ? 0 : readRepostCount(original.getId()));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(author);
            }
            throw ex;
        }
    }

    @Transactional
    public List<String> uploadImages(Long postId, List<MultipartFile> files) {
        var imageDtos = imageService.uploadPostImages(postId, files);
        return imageDtos.stream()
                .map(PostImageDto::url)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostImageDto> getPostImages(Long postId) {
        return imageService.listPostImages(postId);
    }

    public CheerPost findPostById(Long postId) {
        return postRepo.findById(Objects.requireNonNull(postId))
                .orElseThrow(() -> new java.util.NoSuchElementException("게시글을 찾을 수 없습니다: " + postId));
    }

    private CheerPost findPostForRepost(Long postId) {
        try {
            return findPostById(postId);
        } catch (java.util.NoSuchElementException ex) {
            throw new RepostTargetNotFoundException(REPOST_TARGET_NOT_FOUND_CODE, REPOST_TARGET_NOT_FOUND_ERROR);
        }
    }

    private CheerPost resolveRepostActionTarget(UserEntity actor, Long postId, boolean isQuote) {
        CheerPost target = findPostForRepost(postId);
        CheerPost resolvedTarget = isQuote ? target : resolveRepostRootPost(target);
        validateRepostPolicy(actor, target, resolvedTarget, isQuote);
        return resolvedTarget;
    }

    private void validateRepostPolicy(UserEntity actor, CheerPost originalTarget, CheerPost resolvedTarget, boolean isQuote) {
        if (actor.getId().equals(originalTarget.getAuthor().getId())) {
            throw new RepostSelfNotAllowedException(REPOST_SELF_NOT_ALLOWED_CODE, REPOST_NOT_ALLOWED_SELF_ERROR);
        }

        if (isQuote && originalTarget.isRepost()) {
            throw new RepostNotAllowedException(REPOST_QUOTE_NOT_ALLOWED_CODE, REPOST_QUOTE_NOT_ALLOWED_ERROR);
        }
        validateRepostTargetPolicy(actor, resolvedTarget);
    }

    // --- Helpers ---

    private UserEntity resolveWriteAuthor(UserEntity me) {
        if (me == null || me.getId() == null) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        Long principalUserId = getAuthenticationUserId();
        if (principalUserId != null && !principalUserId.equals(me.getId())) {
            log.warn("resolveWriteAuthor - token principal mismatch. meId={}, principalId={}", me.getId(),
                    principalUserId);
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        log.debug("resolveWriteAuthor - loading user for write with lock. userId={}", me.getId());
        UserEntity author = userRepo.findByIdForWrite(me.getId())
                .orElseThrow(() -> new InvalidAuthorException(
                        "인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요."));
        ensureAuthorRecordStillExists(author);

        try {
            entityManager.refresh(author);
        } catch (EntityNotFoundException e) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        return author;
    }

    private UserEntity ensureAuthorRecordStillExists(UserEntity author) {
        if (author == null || author.getId() == null) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        Integer tokenVersion = getAuthenticationTokenVersion();

        if (tokenVersion == null) {
            boolean hasUsableAuthor = userRepo.lockUsableAuthorForWrite(author.getId()).isPresent();
            if (!hasUsableAuthor) {
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
            }
        } else {
            boolean hasUsableAuthor = userRepo.lockUsableAuthorForWriteWithTokenVersion(author.getId(), tokenVersion)
                    .isPresent();
            if (!hasUsableAuthor) {
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
            }
        }

        UserEntity freshAuthor = userRepo.findByIdForWrite(author.getId())
                .orElseThrow(() -> new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요."));

        if (tokenVersion != null) {
            int currentTokenVersion = freshAuthor.getTokenVersion() == null ? 0 : freshAuthor.getTokenVersion();
            if (currentTokenVersion != tokenVersion) {
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
            }
        }

        if (!freshAuthor.isEnabled() || !isAccountUsableForWrite(freshAuthor)) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        return freshAuthor;
    }

    private boolean isAccountUsableForWrite(UserEntity user) {
        if (!user.isLocked()) {
            return true;
        }
        if (user.getLockExpiresAt() == null) {
            return false;
        }
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
        return hasAuthorColumn && (lower.contains("users") || lower.contains("cheer_post"));
    }

    private RepostToggleResponse getSimpleRepostState(UserEntity author, CheerPost original) {
        boolean isCurrentlyReposted = repostRepo.existsByPostIdAndUserId(original.getId(), author.getId()) ||
                postRepo
                        .findByAuthorAndRepostOfAndRepostType(author, original, CheerPost.RepostType.SIMPLE)
                .isPresent();
        return new RepostToggleResponse(isCurrentlyReposted, readRepostCount(original.getId()));
    }

    private CheerPost requireRepostTarget(CheerPost repost) {
        CheerPost target = repost.getRepostOf();
        return target;
    }

    private PostType determinePostType(CreatePostReq req, UserEntity user) {
        if (user != null && "ROLE_ADMIN".equals(user.getRole()) && "NOTICE".equals(req.postType())) {
            return PostType.NOTICE;
        }
        return PostType.NORMAL;
    }

    private CheerPost buildNewPost(CreatePostReq req, UserEntity author, PostType postType, String normalizedTeamId) {
        final String finalTeamId;
        String requestTeamId = normalizedTeamId;

        if (postType == PostType.NOTICE && (requestTeamId == null || requestTeamId.isBlank())) {
            finalTeamId = GLOBAL_TEAM_ID;
        } else {
            finalTeamId = requestTeamId;
        }

        var team = teamRepo.findById(Objects.requireNonNull(finalTeamId))
                .orElseThrow(() -> new java.util.NoSuchElementException("팀을 찾을 수 없습니다: " + finalTeamId));

        return CheerPost.builder()
                .author(author)
                .team(team)
                .shareMode(resolveShareMode(req.shareMode(), null))
                .sourceUrl(req.sourceUrl())
                .sourceTitle(req.sourceTitle())
                .sourceAuthor(req.sourceAuthor())
                .sourceLicense(req.sourceLicense())
                .sourceLicenseUrl(req.sourceLicenseUrl())
                .sourceChangedNote(req.sourceChangedNote())
                .sourceSnapshotType(req.sourceSnapshotType())
                .content(sanitizePostContent(req.content()))
                .postType(postType)
                .build();
    }

    private void updatePostContent(CheerPost post, UpdatePostReq req) {
        post.setContent(req.content());
        if (req.shareMode() != null || hasAnySourceMeta(req)) {
            CheerPost.ShareMode nextMode = req.shareMode() != null
                    ? resolveShareMode(req.shareMode(), post.getRepostType())
                    : post.getShareMode();
            post.setShareMode(nextMode);
            post.setSourceUrl(req.sourceUrl());
            post.setSourceTitle(req.sourceTitle());
            post.setSourceAuthor(req.sourceAuthor());
            post.setSourceLicense(req.sourceLicense());
            post.setSourceLicenseUrl(req.sourceLicenseUrl());
            post.setSourceChangedNote(req.sourceChangedNote());
            post.setSourceSnapshotType(req.sourceSnapshotType());
        }
        // UpdatedAt is handled by Auditing or we set it if manually needed?
        // Usually @LastModifiedDate handles it. CheerService didn't seem to set it
        // manually.
        // But if needed:
        // post.setUpdatedAt(LocalDateTime.now());
    }

    private String sanitizePostContent(String content) {
        return (content == null || content.isBlank()) ? "" : content;
    }

    private CheerPost.ShareMode resolveShareMode(CheerPost.ShareMode requested, CheerPost.RepostType repostType) {
        if (repostType == CheerPost.RepostType.QUOTE) {
            return CheerPost.ShareMode.INTERNAL_QUOTE;
        }
        if (requested == null) {
            return CheerPost.ShareMode.INTERNAL_REPOST;
        }
        return requested;
    }

    private boolean hasAnySourceMeta(UpdatePostReq req) {
        return req.sourceUrl() != null
                || req.sourceTitle() != null
                || req.sourceAuthor() != null
                || req.sourceLicense() != null
                || req.sourceLicenseUrl() != null
                || req.sourceChangedNote() != null
                || req.sourceSnapshotType() != null;
    }

    private void validateSharePolicy(
            CheerPost.ShareMode mode,
            String content,
            String sourceUrl,
            String sourceLicense) {
        CheerPost.ShareMode effectiveMode = mode != null ? mode : CheerPost.ShareMode.INTERNAL_REPOST;
        EnumSet<CheerPost.ShareMode> externalModes = EnumSet.of(
                CheerPost.ShareMode.EXTERNAL_LINK,
                CheerPost.ShareMode.EXTERNAL_COPY,
                CheerPost.ShareMode.EXTERNAL_EMBED,
                CheerPost.ShareMode.EXTERNAL_SUMMARY);

        if (!externalModes.contains(effectiveMode)) {
            return;
        }

        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("외부 콘텐츠 공유 시 출처 URL은 필수입니다.");
        }

        if (effectiveMode == CheerPost.ShareMode.EXTERNAL_COPY) {
            if (sourceLicense == null || sourceLicense.isBlank()) {
                throw new IllegalArgumentException("EXTERNAL_COPY 모드는 라이선스 정보가 필요합니다.");
            }

            String normalized = sanitizePostContent(content);
            if (normalized.length() > 2000) {
                throw new IllegalArgumentException("EXTERNAL_COPY 모드의 본문은 2000자 이하로 제한됩니다.");
            }
        }
    }

    private void flagPotentialSourceSpam(Long authorId, String sourceUrl) {
        if (authorId == null || sourceUrl == null || sourceUrl.isBlank()) {
            return;
        }
        long count = postRepo.countByAuthor_IdAndSourceUrlAndCreatedAtAfter(
                authorId,
                sourceUrl,
                java.time.Instant.now().minusSeconds(24 * 60 * 60));
        if (count >= 3) {
            log.warn("Potential duplicate external repost spam. authorId={}, sourceUrl={}, count24h={}",
                    authorId,
                    sourceUrl,
                    count);
        }
    }

    private void sendNewPostNotificationToFollowers(CheerPost post, UserEntity author) {
        try {
            List<Long> notifyUserIds = followService.getFollowersWithNotifyEnabled(author.getId());
            if (notifyUserIds.isEmpty())
                return;

            String authorName = author.getName() != null && !author.getName().isBlank() ? author.getName()
                    : author.getHandle();

            for (Long userId : notifyUserIds) {
                try {
                    notificationService.createNotification(
                            userId,
                            com.example.notification.entity.Notification.NotificationType.FOLLOWING_NEW_POST,
                            "새 게시글",
                            authorName + "님이 새 게시글을 작성했습니다.",
                            post.getId());
                } catch (Exception e) {
                    log.warn("팔로워 알림 전송 실패: userId={}, postId={}, error={}", userId, post.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("팔로워 알림 전송 중 오류: postId={}, error={}", post.getId(), e.getMessage());
        }
    }

    private CheerPost resolveRepostRootPost(CheerPost post) {
        CheerPost current = post;
        int hops = 0;
        while (current.isRepost()) {
            CheerPost parent = current.getRepostOf();
            if (parent == null) {
                throw new RepostTargetNotFoundException(REPOST_TARGET_NOT_FOUND_CODE, REPOST_TARGET_NOT_FOUND_ERROR);
            }
            current = parent;
            if (++hops > 32) {
                throw new RepostNotAllowedException(REPOST_CYCLE_DETECTED_CODE, REPOST_CYCLE_DETECTED_ERROR);
            }
        }
        return current;
    }

    private void validateRepostTargetPolicy(UserEntity actor, CheerPost target) {
        if (actor.getId().equals(target.getAuthor().getId())) {
            throw new RepostSelfNotAllowedException(REPOST_SELF_NOT_ALLOWED_CODE, REPOST_NOT_ALLOWED_SELF_ERROR);
        }

        if (blockService.hasBidirectionalBlock(actor.getId(), target.getAuthor().getId())) {
            throw new RepostNotAllowedException(REPOST_NOT_ALLOWED_BLOCKED_CODE, REPOST_NOT_ALLOWED_BLOCKED_ERROR);
        }

        if (target.getAuthor().isPrivateAccount()) {
            throw new RepostNotAllowedException(REPOST_NOT_ALLOWED_PRIVATE_CODE, REPOST_NOT_ALLOWED_PRIVATE_ERROR);
        }
    }

    private void validateRepostCancelRequest(UserEntity actor, CheerPost repost) {
        if (!repost.isRepost()) {
            throw new RepostNotAllowedException(REPOST_NOT_A_REPOST_CODE, REPOST_NOT_A_REPOST_ERROR);
        }

        if (!repost.getAuthor().getId().equals(actor.getId())) {
            throw new RepostSelfNotAllowedException(REPOST_CANCEL_NOT_ALLOWED_CODE, REPOST_CANCEL_NOT_ALLOWED_ERROR);
        }
    }

    private void createRepostTracking(CheerPost originalPost, UserEntity author) {
        CheerPostRepost repostTracking = new CheerPostRepost();
        repostTracking.setId(new CheerPostRepost.Id(originalPost.getId(), author.getId()));
        repostTracking.setPost(originalPost);
        repostTracking.setUser(author);
        repostRepo.save(repostTracking);
    }

    private void sendRepostNotification(UserEntity actor, CheerPost originalPost, String notificationTitle) {
        if (actor.getId().equals(originalPost.getAuthor().getId())) {
            return;
        }

        try {
            String actorName = actor.getName() != null && !actor.getName().isBlank() ? actor.getName() : actor.getEmail();
            notificationService.createNotification(
                    Objects.requireNonNull(originalPost.getAuthor().getId()),
                    com.example.notification.entity.Notification.NotificationType.POST_REPOST,
                    notificationTitle,
                    actorName + "님이 회원님의 게시글을 리포스트했습니다.",
                    originalPost.getId());
        } catch (Exception e) {
            log.warn("리포스트 알림 생성 실패: postId={}, error={}", originalPost.getId(), e.getMessage());
        }
    }

    private boolean isRepostDuplicateViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("uq_cheer_post_simple_repost")
                || (lower.contains("duplicate key") && lower.contains("repost_type") && lower.contains("repost_of_id"))
                || (lower.contains("repost_of_id") && lower.contains("repost_type"))
                || (lower.contains("cheer_post_repost") && lower.contains("duplicate key"))
                || (lower.contains("cheer_post_repost_pkey"));
    }

    private int readRepostCount(Long postId) {
        Integer count = postRepo.findRepostCountById(postId);
        return count == null ? 0 : count;
    }

    public void updateHotScore(CheerPost post) {
        int combinedViews = post.getViews()
                + (redisPostService.getViewCount(post.getId()) != null ? redisPostService.getViewCount(post.getId())
                        : 0);
        java.time.Instant now = java.time.Instant.now();

        double timeDecayScore = popularFeedScoringService.calculateTimeDecayScore(post, combinedViews, now);
        double engagementRateScore = popularFeedScoringService.calculateEngagementRateScore(post, combinedViews);

        redisPostService.updateHotScore(post.getId(), timeDecayScore,
                com.example.cheerboard.service.PopularFeedAlgorithm.TIME_DECAY);
        redisPostService.updateHotScore(post.getId(), engagementRateScore,
                com.example.cheerboard.service.PopularFeedAlgorithm.ENGAGEMENT_RATE);
    }
}
