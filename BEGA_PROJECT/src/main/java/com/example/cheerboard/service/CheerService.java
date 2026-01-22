package com.example.cheerboard.service;

import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerCommentLike;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.dto.ReportRequest;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.domain.CheerPostBookmark;
import com.example.cheerboard.dto.BookmarkResponse;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.example.auth.entity.UserEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.notification.service.NotificationService;
import com.example.common.exception.UserNotFoundException;
import com.example.common.service.AIModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.cheerboard.service.CheerServiceConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerService {

    private final CheerPostRepo postRepo;
    private final CheerCommentRepo commentRepo;
    private final CheerPostLikeRepo likeRepo;
    private final CheerCommentLikeRepo commentLikeRepo;
    private final CheerBookmarkRepo bookmarkRepo;
    private final CheerReportRepo reportRepo; // [NEW]
    private final TeamRepository teamRepo;
    private final com.example.auth.repository.UserRepository userRepo;
    private final CurrentUser current;
    private final NotificationService notificationService;
    private final com.example.cheerboard.storage.service.ImageService imageService;

    // 리팩토링된 컴포넌트들
    private final PermissionValidator permissionValidator;
    private final PostDtoMapper postDtoMapper;
    private final RedisPostService redisPostService;
    private final AIModerationService moderationService;

    // ... (list method remains the same as recently updated, skipping to avoid
    // overwriting)

    // ...

    @Transactional
    public List<String> uploadImages(Long postId,
            java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        // ImageService가 권한 체크 및 업로드 수행
        var imageDtos = imageService.uploadPostImages(postId, files);

        // DTO 리스트를 URL 리스트로 변환
        return imageDtos.stream()
                .map(com.example.cheerboard.storage.dto.PostImageDto::url)
                .filter(url -> url != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<com.example.cheerboard.storage.dto.PostImageDto> getPostImages(Long postId) {
        return imageService.listPostImages(postId);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> list(String teamId, String postTypeStr, Pageable pageable) {
        if (teamId != null && !teamId.isBlank()) {
            UserEntity me = current.getOrNull();
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, teamId, "게시글 조회");
        }

        // PostType 필터링 적용
        PostType postType = null;
        if (postTypeStr != null && !postTypeStr.isBlank()) {
            try {
                postType = PostType.valueOf(postTypeStr);
            } catch (IllegalArgumentException e) {
                // 무시하고 전체 조회하거나 에러 처리 (여기서는 무시)
            }
        }

        Page<CheerPost> page;
        boolean hasSort = pageable.getSort().isSorted();

        // 정렬 조건이 있으면(예: views) Custom Query 대신 기본 JPA Query 사용
        // (findByTeamIdAndPostType)
        // 정렬 조건이 없으면(기본) Notice Pinning 로직 사용 (findAllOrderByPostTypeAndCreatedAt)
        // 단, findAllOrderByPostTypeAndCreatedAt는 postType 필터링 추가됨
        if (hasSort && pageable.getSort().stream().anyMatch(order -> !order.getProperty().equals("createdAt"))) {
            page = postRepo.findByTeamIdAndPostType(teamId, postType, pageable);
        } else {
            // 공지사항 상단 고정 정책: 최근 3일 이내의 공지사항만 상단에 고정
            java.time.Instant cutoffDate = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
            page = postRepo.findAllOrderByPostTypeAndCreatedAt(teamId, postType, cutoffDate, pageable);
        }

        List<Long> postIds = page.hasContent()
                ? page.getContent().stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        UserEntity me = current.getOrNull();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> likedPostIds = new HashSet<>();
        if (me != null && !postIds.isEmpty()) {
            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            // 좋아요 여부 조회
            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalLikes = likedPostIds;
        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                    finalBookmarks.contains(post.getId()), isOwner, imageUrls);
        });
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> search(String q, String teamId, Pageable pageable) {
        Page<CheerPost> page = postRepo.search(q, teamId, pageable);

        List<Long> postIds = page.hasContent()
                ? Objects.requireNonNull(page.getContent()).stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        UserEntity me = current.getOrNull();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> likedPostIds = new HashSet<>();
        if (me != null && !postIds.isEmpty()) {
            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalLikes = likedPostIds;
        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                    finalBookmarks.contains(post.getId()), isOwner, imageUrls);
        });
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getHotPosts(Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = start + pageable.getPageSize() - 1;

        Set<Long> hotPostIds = redisPostService.getHotPostIds(start, end);
        if (hotPostIds.isEmpty()) {
            List<PostSummaryRes> emptyList = Collections.emptyList();
            return new PageImpl<PostSummaryRes>(Objects.requireNonNull(emptyList), Objects.requireNonNull(pageable), 0);
        }

        List<CheerPost> posts = postRepo.findAllById(hotPostIds);
        // Redis 순서(점수 높은 순)를 유지하기 위해 정렬
        Map<Long, CheerPost> postMap = posts.stream()
                .collect(Collectors.toMap(CheerPost::getId, Function.identity()));
        List<CheerPost> sortedPosts = hotPostIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .toList();

        // DTO 변환 로직
        UserEntity me = current.getOrNull();
        List<PostSummaryRes> content = sortedPosts.stream()
                .map(post -> {
                    boolean liked = me != null && isPostLikedByUser(post.getId(), me.getId());
                    boolean isBookmarked = me != null && isPostBookmarkedByUser(post.getId(), me.getId());
                    boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
                    return postDtoMapper.toPostSummaryRes(post, liked, isBookmarked, isOwner);
                })
                .collect(Collectors.toList());

        return new PageImpl<PostSummaryRes>(Objects.requireNonNull(content), Objects.requireNonNull(pageable),
                redisPostService.getHotPostIds(0, 99).size());
    }

    /**
     * 게시글 HOT 점수 업데이트 (Formula: Likes*3 + Comments*2 + Views)
     */
    public void updateHotScore(CheerPost post) {
        // Redis 실시간 조회수 합산
        int combinedViews = getCombinedViewCount(post);
        double score = (post.getLikeCount() * 3.0) + (post.getCommentCount() * 2.0) + combinedViews;
        redisPostService.updateHotScore(post.getId(), score);
    }

    private int getCombinedViewCount(CheerPost post) {
        Integer redisViews = redisPostService.getViewCount(post.getId());
        return post.getViews() + (redisViews != null ? redisViews : 0);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listByUserHandle(String handle, Pageable pageable) {
        Page<CheerPost> page = postRepo.findByAuthor_HandleOrderByCreatedAtDesc(handle, pageable);

        List<Long> postIds = page.hasContent()
                ? page.getContent().stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        UserEntity me = current.getOrNull();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> likedPostIds = new HashSet<>();
        if (me != null && !postIds.isEmpty()) {
            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            // 좋아요 여부 조회
            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalLikes = likedPostIds;
        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                    finalBookmarks.contains(post.getId()), isOwner, imageUrls);
        });
    }

    @Transactional
    public PostDetailRes get(Long id) {
        UserEntity me = current.getOrNull();
        CheerPost post = findPostById(id);

        increaseViewCount(id, post, me);

        boolean liked = me != null && isPostLikedByUser(id, me.getId());
        boolean isBookmarked = me != null && isPostBookmarkedByUser(id, me.getId());
        boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());

        return postDtoMapper.toPostDetailRes(post, liked, isBookmarked, isOwner);
    }

    /**
     * 게시글 조회수 증가 (Redis 활용)
     */
    private void increaseViewCount(Long postId, CheerPost post, UserEntity user) {
        // 작성자가 아닌 경우에만 증가
        if (user == null || !post.getAuthor().getId().equals(user.getId())) {
            redisPostService.incrementViewCount(postId, user != null ? user.getId() : null);
        }
    }

    /**
     * 게시글 ID로 게시글 조회
     */
    private CheerPost findPostById(Long postId) {
        return postRepo.findById(Objects.requireNonNull(postId))
                .orElseThrow(() -> new java.util.NoSuchElementException("게시글을 찾을 수 없습니다: " + postId));
    }

    /**
     * 사용자가 게시글에 좋아요를 눌렀는지 확인
     */
    private boolean isPostLikedByUser(Long postId, Long userId) {
        return likeRepo.existsById(new CheerPostLike.Id(postId, userId));
    }

    private boolean isPostBookmarkedByUser(Long postId, Long userId) {
        return bookmarkRepo.existsById(new CheerPostBookmark.Id(postId, userId));
    }

    @Transactional
    public PostDetailRes createPost(CreatePostReq req) {
        UserEntity me = current.get();

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService
                .checkContent(req.title() + " " + req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        permissionValidator.validateTeamAccess(me, req.teamId(), "게시글 작성");

        PostType postType = determinePostType(req, me);
        CheerPost post = buildNewPost(req, me, postType);
        CheerPost savedPost = postRepo.save(Objects.requireNonNull(post));

        return postDtoMapper.toNewPostDetailRes(savedPost, me);
    }

    /**
     * 게시글 타입 결정 (공지사항 권한 체크 포함)
     */
    private PostType determinePostType(CreatePostReq req, UserEntity user) {
        // 관리자가 공지사항으로 체크한 경우에만 NOTICE로 설정
        if (user != null && "ROLE_ADMIN".equals(user.getRole()) && "NOTICE".equals(req.postType())) {
            return PostType.NOTICE;
        }
        // 그 외 모든 경우는 일반 게시글로 처리
        return PostType.NORMAL;
    }

    /**
     * 새 게시글 엔티티 생성
     */
    private CheerPost buildNewPost(CreatePostReq req, UserEntity author, PostType postType) {
        log.debug("buildNewPost - teamId={}, postType={}", req.teamId(), postType);

        final String finalTeamId;
        String requestTeamId = req.teamId();

        if (postType == PostType.NOTICE && (requestTeamId == null || requestTeamId.isBlank())) {
            finalTeamId = GLOBAL_TEAM_ID;
            log.debug("Admin notice post: resolved teamId to GLOBAL_TEAM_ID: {}", finalTeamId);
        } else {
            finalTeamId = requestTeamId;
        }

        var team = teamRepo.findById(Objects.requireNonNull(finalTeamId))
                .orElseThrow(() -> new java.util.NoSuchElementException("팀을 찾을 수 없습니다: " + finalTeamId));
        log.debug("buildNewPost - team lookup succeeded: {}", team.getTeamId());

        CheerPost post = CheerPost.builder()
                .author(author)
                .team(team)
                .title(req.title())
                .content(req.content())
                .postType(postType)
                .build();

        log.debug("buildNewPost - resolved post team={}", post.getTeam() != null ? post.getTeam().getTeamId() : "NULL");
        return post;
    }

    @Transactional
    public PostDetailRes updatePost(Long id, UpdatePostReq req) {
        UserEntity me = current.get();
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "게시글 수정");

        updatePostContent(post, req);

        boolean liked = isPostLikedByUser(id, me.getId());
        boolean isBookmarked = isPostBookmarkedByUser(id, me.getId());
        return postDtoMapper.toPostDetailRes(post, liked, isBookmarked, true);
    }

    /**
     * 게시글 내용 업데이트
     */
    private void updatePostContent(CheerPost post, UpdatePostReq req) {
        post.setTitle(req.title());
        post.setContent(req.content());
    }

    @Transactional
    public void deletePost(Long id) {
        UserEntity me = current.get();
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "게시글 삭제");

        // 1. Soft Delete (안전장치 - 트랜잭션 도중 실패 대비)
        post.setDeleted(true);
        postRepo.save(post);

        // 2. 스토리지 삭제 시도
        boolean storageClean = imageService.deleteImagesByPostId(post.getId());

        // 3. 스토리지 삭제 완료 시 Hard Delete (DB Clean)
        if (storageClean) {
            postRepo.delete(post);
        } else {
            log.warn("게시글 삭제 중 일부 이미지 삭제 실패. Soft Delete 상태 유지 및 스케줄러 위임: postId={}", id);
        }
    }

    @Transactional
    public LikeToggleResponse toggleLike(Long postId) {
        UserEntity me = current.get();
        CheerPost post = findPostById(postId);

        // 좋아요는 모든 팀에서 허용

        CheerPostLike.Id likeId = new CheerPostLike.Id(post.getId(), me.getId());

        boolean liked;
        int likes;

        if (likeRepo.existsById(likeId)) {
            // 좋아요 취소
            likeRepo.deleteById(likeId);
            likes = Math.max(0, post.getLikeCount() - 1);
            post.setLikeCount(likes);
            liked = false;

            // 작성자 포인트 차감 (Entity Update)
            UserEntity author = userRepo.findById(Objects.requireNonNull(post.getAuthor().getId()))
                    .orElseThrow(() -> new UserNotFoundException(post.getAuthor().getId()));
            author.deductCheerPoints(1); // Entity method
            userRepo.save(author);
            log.info("Points deducted for user {}: -1 (Entity Update)", author.getId());

        } else {
            // 좋아요 추가
            CheerPostLike like = new CheerPostLike();
            like.setId(likeId);
            like.setPost(post);
            like.setUser(me);
            likeRepo.save(like);
            likes = post.getLikeCount() + 1;
            post.setLikeCount(likes);
            liked = true;

            // 작성자 포인트 증가 (Entity Update)
            UserEntity author = userRepo.findById(Objects.requireNonNull(post.getAuthor().getId()))
                    .orElseThrow(() -> new UserNotFoundException(post.getAuthor().getId()));
            author.addCheerPoints(1); // Entity method
            userRepo.save(author);
            log.info("Points awarded to user {}: +1 (Entity Update)", author.getId());

            // 게시글 작성자에게 알림 (본인이 아닐 때만)
            if (!author.getId().equals(me.getId())) {
                try {
                    String authorName = me.getName() != null && !me.getName().isBlank()
                            ? me.getName()
                            : me.getEmail();

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

        postRepo.save(Objects.requireNonNull(post));
        updateHotScore(post);
        return new LikeToggleResponse(liked, likes);
    }

    @Transactional
    public BookmarkResponse toggleBookmark(Long postId) {
        UserEntity me = current.get();
        CheerPost post = findPostById(postId);
        CheerPostBookmark.Id bookmarkId = new CheerPostBookmark.Id(postId, me.getId());

        boolean bookmarked;
        if (bookmarkRepo.existsById(bookmarkId)) {
            bookmarkRepo.deleteById(bookmarkId);
            bookmarked = false;
        } else {
            CheerPostBookmark bookmark = new CheerPostBookmark();
            bookmark.setId(bookmarkId);
            bookmark.setPost(post);
            bookmark.setUser(me);
            bookmarkRepo.save(Objects.requireNonNull(bookmark));
            bookmarked = true;
        }
        return new BookmarkResponse(bookmarked);
    }

    @Transactional
    public int repost(Long postId) {
        // UserEntity me = current.get(); // Could be used to track who reposted
        CheerPost post = findPostById(postId);

        int newCount = post.getRepostCount() + 1;
        post.setRepostCount(newCount);
        postRepo.save(post);

        updateHotScore(post);
        return newCount;
    }

    @Transactional
    public void reportPost(Long postId, ReportRequest req) {
        UserEntity reporter = current.get();
        CheerPost post = findPostById(postId);

        CheerPostReport report = CheerPostReport.builder()
                .post(post)
                .reporter(reporter)
                .reason(req.reason())
                .description(req.description())
                .build();

        reportRepo.save(Objects.requireNonNull(report));
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getBookmarkedPosts(Pageable pageable) {
        UserEntity me = current.get();
        Page<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdOrderByCreatedAtDesc(me.getId(), pageable);

        List<Long> postIds = bookmarks.hasContent()
                ? bookmarks.getContent().stream().map(b -> b.getPost().getId()).toList()
                : Collections.emptyList();
        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        Set<Long> likedPostIds = new HashSet<>();
        if (!postIds.isEmpty()) {
            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());
        }
        final Set<Long> finalLikes = likedPostIds;

        return bookmarks.map(b -> {
            boolean isOwner = permissionValidator.isOwnerOrAdmin(me, b.getPost().getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(b.getPost().getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(b.getPost(), finalLikes.contains(b.getPost().getId()), true, isOwner,
                    imageUrls);
        });
    }

    @Transactional(readOnly = true)
    public Page<CommentRes> listComments(Long postId, Pageable pageable) {
        // 최상위 댓글만 조회 (대댓글은 각 댓글의 replies에 포함됨)
        Page<CheerComment> page = commentRepo
                .findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(Objects.requireNonNull(postId), pageable);

        if (!page.hasContent()) {
            return new PageImpl<>(Objects.requireNonNull(List.of()), Objects.requireNonNull(pageable),
                    page.getTotalElements());
        }

        List<Long> commentIds = page.getContent().stream()
                .map(CheerComment::getId)
                .toList();

        List<CheerComment> hydrated = commentRepo.findWithRepliesByIdIn(commentIds);
        Map<Long, CheerComment> hydratedById = hydrated.stream()
                .collect(Collectors.toMap(CheerComment::getId, Function.identity(), (a, b) -> a));

        List<CheerComment> comments = commentIds.stream()
                .map(hydratedById::get)
                .filter(Objects::nonNull)
                .toList();

        UserEntity me = current.getOrNull();
        Set<Long> likedCommentIds = new HashSet<>();

        if (me != null && !comments.isEmpty()) {
            // 모든 댓글 ID 수집 (대댓글 포함)
            List<Long> allCommentIds = collectAllCommentIds(comments);

            // 한 번의 쿼리로 좋아요 여부 확인
            if (!allCommentIds.isEmpty()) {
                likedCommentIds = new HashSet<>(
                        commentLikeRepo.findLikedCommentIdsByUserIdAndCommentIdIn(me.getId(), allCommentIds));
            }
        }

        final Set<Long> finalLikedIds = likedCommentIds;
        List<CommentRes> mapped = comments.stream()
                .map(comment -> toCommentResWithLikedSet(comment, finalLikedIds))
                .toList();
        return new PageImpl<>(Objects.requireNonNull(mapped), Objects.requireNonNull(pageable),
                page.getTotalElements());
    }

    /**
     * 댓글과 대댓글의 모든 ID 수집
     */
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
    public CommentRes addComment(Long postId, CreateCommentReq req) {
        UserEntity me = current.get();
        CheerPost post = findPostById(postId);
        permissionValidator.validateTeamAccess(me, post.getTeamId(), "댓글 작성");

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        // 중복 댓글 체크: 직전 3초 이내 동일 작성자·게시글·내용 댓글 확인
        checkDuplicateComment(post.getId(), me.getId(), req.content(), null);

        CheerComment comment = saveNewComment(post, me, req);
        incrementCommentCount(post);
        updateHotScore(post);

        // 게시글 작성자에게 알림 (본인이 아닐 때만)
        if (!post.getAuthor().getId().equals(me.getId())) {
            try {
                String authorName = me.getName() != null && !me.getName().isBlank()
                        ? me.getName()
                        : me.getEmail();

                notificationService.createNotification(
                        post.getAuthor().getId(),
                        com.example.notification.entity.Notification.NotificationType.POST_COMMENT,
                        "새 댓글",
                        authorName + "님이 회원님의 게시글에 댓글을 남겼습니다.",
                        post.getId());
            } catch (Exception e) {
                log.warn("댓글 알림 생성 실패: postId={}, error={}", post.getId(), e.getMessage());
            }
        }

        return toCommentRes(comment);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        UserEntity me = current.get();
        CheerComment comment = findCommentById(commentId);
        permissionValidator.validateOwnerOrAdmin(me, comment.getAuthor(), "댓글 삭제");

        CheerPost post = comment.getPost();
        commentRepo.delete(comment);

        // 실제 DB에서 댓글 수 재계산 (댓글 + 대댓글 모두 포함)
        // Null type safety 해결을 위해 primitive type 변환 후 전달
        Long actualCount = commentRepo.countByPostId(Objects.requireNonNull(post.getId()).longValue());
        post.setCommentCount(actualCount != null ? actualCount.intValue() : 0);
    }

    /**
     * 댓글 ID로 댓글 조회
     */
    private CheerComment findCommentById(Long commentId) {
        Objects.requireNonNull(commentId, "댓글 ID는 null일 수 없습니다");
        return commentRepo.findById(commentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("댓글을 찾을 수 없습니다: " + commentId));
    }

    /**
     * 새 댓글 저장
     */
    private CheerComment saveNewComment(CheerPost post, UserEntity author, CreateCommentReq req) {
        return commentRepo.save(Objects.requireNonNull(CheerComment.builder()
                .post(post)
                .author(author)
                .content(req.content())
                .build()));
    }

    /**
     * 게시글 댓글 수 증가
     */
    private void incrementCommentCount(CheerPost post) {
        post.setCommentCount(post.getCommentCount() + 1);
    }

    /**
     * CheerComment를 CommentRes로 변환 (단일 댓글용 - 새 댓글 작성 시 사용)
     */
    private CommentRes toCommentRes(CheerComment comment) {
        UserEntity me = current.getOrNull();
        boolean likedByMe = me != null && isCommentLikedByUser(comment.getId(), me.getId());

        // 대댓글 변환 (재귀적으로 처리)
        List<CommentRes> replies = comment.getReplies().stream()
                .map(this::toCommentRes)
                .collect(Collectors.toList());

        return new CommentRes(
                comment.getId(),
                resolveDisplayName(comment.getAuthor()),
                comment.getAuthor().getEmail(),
                comment.getAuthor().getFavoriteTeamId(),
                comment.getAuthor().getProfileImageUrl(),
                comment.getAuthor().getHandle(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getLikeCount(),
                likedByMe,
                replies);
    }

    /**
     * CheerComment를 CommentRes로 변환 (일괄 조회 최적화 버전)
     * 미리 조회한 likedCommentIds를 사용하여 N+1 문제 방지
     */
    private CommentRes toCommentResWithLikedSet(CheerComment comment, Set<Long> likedCommentIds) {
        boolean likedByMe = likedCommentIds.contains(comment.getId());

        // 대댓글 변환 (재귀적으로 처리, 동일한 likedCommentIds 세트 사용)
        List<CommentRes> replies = comment.getReplies().stream()
                .map(reply -> toCommentResWithLikedSet(reply, likedCommentIds))
                .collect(Collectors.toList());

        return new CommentRes(
                comment.getId(),
                resolveDisplayName(comment.getAuthor()),
                comment.getAuthor().getEmail(),
                comment.getAuthor().getFavoriteTeamId(),
                comment.getAuthor().getProfileImageUrl(),
                comment.getAuthor().getHandle(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getLikeCount(),
                likedByMe,
                replies);
    }

    /**
     * 사용자가 댓글에 좋아요를 눌렀는지 확인
     */
    private boolean isCommentLikedByUser(Long commentId, Long userId) {
        return commentLikeRepo.existsById(new CheerCommentLike.Id(commentId, userId));
    }

    private String resolveDisplayName(UserEntity user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getEmail();
    }

    /**
     * 댓글 좋아요 토글
     */
    @Transactional
    public LikeToggleResponse toggleCommentLike(Long commentId) {
        UserEntity me = current.get();
        CheerComment comment = findCommentById(commentId);

        // 댓글이 속한 게시글의 팀 권한 확인
        permissionValidator.validateTeamAccess(me, comment.getPost().getTeamId(), "댓글 좋아요");

        CheerCommentLike.Id likeId = new CheerCommentLike.Id(comment.getId(), me.getId());

        boolean liked;
        int likes;

        if (commentLikeRepo.existsById(likeId)) {
            // 좋아요 취소
            commentLikeRepo.deleteById(likeId);
            likes = Math.max(0, comment.getLikeCount() - 1);
            comment.setLikeCount(likes);
            liked = false;

            // 댓글 작성자 포인트 차감 (Entity Update)
            UserEntity author = userRepo.findById(Objects.requireNonNull(comment.getAuthor().getId()))
                    .orElseThrow(() -> new UserNotFoundException(comment.getAuthor().getId()));
            author.deductCheerPoints(1);
            userRepo.save(author);
            log.info("Points deducted for comment author {}: -1 (Entity Update)", author.getId());

        } else {
            // 좋아요 추가
            CheerCommentLike like = new CheerCommentLike();
            like.setId(likeId);
            like.setComment(comment);
            like.setUser(me);
            commentLikeRepo.save(like);
            likes = comment.getLikeCount() + 1;
            comment.setLikeCount(likes);
            liked = true;

            // 댓글 작성자 포인트 증가 (Entity Update)
            UserEntity author = userRepo.findById(Objects.requireNonNull(comment.getAuthor().getId()))
                    .orElseThrow(() -> new UserNotFoundException(comment.getAuthor().getId()));
            author.addCheerPoints(1);
            userRepo.save(author);
            log.info("Points awarded to comment author {}: +1 (Entity Update)", author.getId());
        }

        commentRepo.save(Objects.requireNonNull(comment));
        return new LikeToggleResponse(liked, likes);
    }

    /**
     * 대댓글 작성
     */
    @Transactional
    public CommentRes addReply(Long postId, Long parentCommentId, CreateCommentReq req) {
        UserEntity me = current.get();
        CheerPost post = findPostById(postId);
        CheerComment parentComment = findCommentById(parentCommentId);

        // 부모 댓글이 해당 게시글에 속하는지 확인
        if (!parentComment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("부모 댓글이 해당 게시글에 속하지 않습니다.");
        }

        permissionValidator.validateTeamAccess(me, post.getTeamId(), "대댓글 작성");

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        // 중복 대댓글 체크: 직전 3초 이내 동일 작성자·부모댓글·내용 대댓글 확인
        checkDuplicateComment(post.getId(), me.getId(), req.content(), parentCommentId);

        CheerComment reply = saveNewReply(post, parentComment, me, req);
        incrementCommentCount(post);
        updateHotScore(post);

        // 원댓글 작성자에게 알림 (본인이 아닐 때만)
        if (!parentComment.getAuthor().getId().equals(me.getId())) {
            try {
                String authorName = me.getName() != null && !me.getName().isBlank()
                        ? me.getName()
                        : me.getEmail();

                notificationService.createNotification(
                        Objects.requireNonNull(parentComment.getAuthor().getId()),
                        com.example.notification.entity.Notification.NotificationType.COMMENT_REPLY,
                        "새 대댓글",
                        authorName + "님이 회원님의 댓글에 답글을 남겼습니다.",
                        post.getId());
            } catch (Exception e) {
                log.warn("대댓글 알림 생성 실패: postId={}, parentCommentId={}, error={}",
                        post.getId(), parentCommentId, e.getMessage());
            }
        }

        return toCommentRes(reply);
    }

    /**
     * 새 대댓글 저장
     */
    private CheerComment saveNewReply(CheerPost post, CheerComment parentComment, UserEntity author,
            CreateCommentReq req) {
        return commentRepo.save(Objects.requireNonNull(CheerComment.builder()
                .post(post)
                .parentComment(parentComment)
                .author(author)
                .content(req.content())
                .build()));
    }

    /**
     * 중복 댓글/대댓글 체크
     * 직전 3초 이내 동일 작성자·게시글·내용·부모댓글 조합이 있는지 확인
     */
    private void checkDuplicateComment(Long postId, Long authorId, String content, Long parentCommentId) {
        java.time.Instant threeSecondsAgo = java.time.Instant.now().minusSeconds(3);
        boolean isDuplicate;

        if (parentCommentId == null) {
            // 최상위 댓글 중복 체크
            isDuplicate = commentRepo.existsByPostIdAndAuthorIdAndContentAndParentCommentIsNullAndCreatedAtAfter(
                    postId, authorId, content, threeSecondsAgo);
        } else {
            // 대댓글 중복 체크
            isDuplicate = commentRepo.existsByPostIdAndAuthorIdAndContentAndParentCommentIdAndCreatedAtAfter(
                    postId, authorId, content, parentCommentId, threeSecondsAgo);
        }

        if (isDuplicate) {
            throw new IllegalStateException("중복된 댓글입니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
