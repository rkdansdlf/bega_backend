package com.example.cheerboard.service;

import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerCommentLike;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.CheerPostRepost;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostChangesResponse;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.dto.QuoteRepostReq;
import com.example.cheerboard.dto.ReportRequest;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.domain.CheerPostBookmark;
import com.example.cheerboard.dto.BookmarkResponse;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.FollowService;
import com.example.auth.service.BlockService;
import com.example.kbo.repository.TeamRepository;
import com.example.kbo.util.TeamCodeNormalizer;
import com.example.notification.service.NotificationService;
import com.example.common.exception.UserNotFoundException;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.service.AIModerationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.time.LocalDateTime;
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
    private final CheerPostRepostRepo repostRepo;
    private final CheerCommentLikeRepo commentLikeRepo;
    private final CheerBookmarkRepo bookmarkRepo;
    private final CheerReportRepo reportRepo; // [NEW]
    private final TeamRepository teamRepo;
    private final com.example.auth.repository.UserRepository userRepo;
    private final CurrentUser current;
    private final NotificationService notificationService;
    private final com.example.cheerboard.storage.service.ImageService imageService;
    private final FollowService followService;
    private final BlockService blockService;
    private final EntityManager entityManager;

    // 리팩토링된 컴포넌트들
    private final PermissionValidator permissionValidator;
    private final PostDtoMapper postDtoMapper;
    private final RedisPostService redisPostService;
    private final PopularFeedScoringService popularFeedScoringService;
    private final AIModerationService moderationService;
    private final com.example.profile.storage.service.ProfileImageService profileImageService;

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

    private String normalizeTeamId(String teamId) {
        return TeamCodeNormalizer.normalize(teamId);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> list(String teamId, String postTypeStr, Pageable pageable) {
        String normalizedTeamId = normalizeTeamId(teamId);
        if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
            UserEntity me = current.getOrNull();
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
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
        // [NEW] 차단 유저 ID 목록 (내가 차단한 유저 + 나를 차단한 유저)
        java.util.Set<Long> excludedIds = getExcludedUserIds();
        log.debug("List - excludedIds size: {}", excludedIds.size());

        if (hasSort && pageable.getSort().stream().anyMatch(order -> !order.getProperty().equals("createdAt"))) {
            page = postRepo.findByTeamIdAndPostType(normalizedTeamId, postType, excludedIds, pageable);
        } else {
            // 공지사항 상단 고정 정책: 최근 3일 이내의 공지사항만 상단에 고정
            java.time.Instant cutoffDate = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
            page = postRepo.findAllOrderByPostTypeAndCreatedAt(normalizedTeamId, postType, cutoffDate, excludedIds,
                    pageable);
        }

        List<Long> postIds = page.hasContent()
                ? page.getContent().stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        // 리포스트 원본 이미지 벌크 프리페치
        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(page.getContent());

        // Redis 배치 조회 (조회수, HOT 상태)
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = redisPostService.getCachedHotStatuses(postIds);
        Map<Long, Integer> bookmarkCountMap = getBookmarkCountMap(postIds);

        UserEntity me = current.getOrNull();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> repostedPostIds = new HashSet<>();
        if (me != null && !postIds.isEmpty()) {
            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostRepost> reposts = repostRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            repostedPostIds = reposts.stream().map(r -> r.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalLikes = likedPostIds;
        final Set<Long> finalReposts = repostedPostIds;
        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                    finalBookmarks.contains(post.getId()), isOwner, finalReposts.contains(post.getId()),
                    bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                    viewCountMap, hotStatusMap, repostImageUrls);
        });
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> search(String q, String teamId, Pageable pageable) {
        // [NEW] 차단 유저 ID 목록
        String normalizedTeamId = normalizeTeamId(teamId);
        java.util.Set<Long> excludedIds = getExcludedUserIds();
        Page<CheerPost> page = postRepo.search(q, normalizedTeamId, excludedIds, pageable);

        List<Long> postIds = page.hasContent()
                ? Objects.requireNonNull(page.getContent()).stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(page.getContent());
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = redisPostService.getCachedHotStatuses(postIds);
        Map<Long, Integer> bookmarkCountMap = getBookmarkCountMap(postIds);

        UserEntity me = current.getOrNull();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> repostedPostIds = new HashSet<>();
        if (me != null && !postIds.isEmpty()) {
            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostRepost> reposts = repostRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            repostedPostIds = reposts.stream().map(r -> r.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalLikes = likedPostIds;
        final Set<Long> finalReposts = repostedPostIds;
        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                    finalBookmarks.contains(post.getId()), isOwner, finalReposts.contains(post.getId()),
                    bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                    viewCountMap, hotStatusMap, repostImageUrls);
        });
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getHotPosts(Pageable pageable) {
        return getHotPosts(pageable, PopularFeedAlgorithm.HYBRID.name());
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getHotPosts(Pageable pageable, String algorithmRaw) {
        PopularFeedAlgorithm algorithm = PopularFeedAlgorithm.from(algorithmRaw);
        if (algorithm == PopularFeedAlgorithm.HYBRID) {
            return getHybridHotPosts(pageable);
        }
        return getGlobalHotPosts(pageable, algorithm);
    }

    private Page<PostSummaryRes> getGlobalHotPosts(Pageable pageable, PopularFeedAlgorithm algorithm) {
        int start = (int) pageable.getOffset();
        int end = start + pageable.getPageSize() - 1;

        Set<Long> hotPostIds = redisPostService.getHotPostIds(start, end, algorithm);
        if (hotPostIds.isEmpty()) {
            List<PostSummaryRes> emptyList = Collections.emptyList();
            return new PageImpl<>(Objects.requireNonNull(emptyList), Objects.requireNonNull(pageable),
                    redisPostService.getHotListSize(algorithm));
        }

        List<CheerPost> posts = postRepo.findAllByIdWithGraph(hotPostIds);
        UserEntity me = current.getOrNull();

        // [NEW] 차단 유저 필터링 (메모리 내 필터링 - Hot ID 목록은 Redis에서 오므로)
        java.util.Set<Long> excludedIds = getExcludedUserIds();

        // Redis 순서(점수 높은 순)를 유지하기 위해 정렬 및 차단 필터링
        Map<Long, CheerPost> postMap = posts.stream()
                .collect(Collectors.toMap(CheerPost::getId, Function.identity()));
        List<CheerPost> sortedPosts = hotPostIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .filter(post -> !excludedIds.contains(post.getAuthor().getId())) // 차단 필터
                .toList();

        List<PostSummaryRes> content = toHotPostSummary(sortedPosts, me);
        return new PageImpl<>(Objects.requireNonNull(content), Objects.requireNonNull(pageable),
                redisPostService.getHotListSize(algorithm));
    }

    private Page<PostSummaryRes> getHybridHotPosts(Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = start + pageable.getPageSize() - 1;
        int candidateEnd = Math.min(999, end + 200);

        Set<Long> candidateIds = redisPostService.getHotPostIds(0, candidateEnd, PopularFeedAlgorithm.TIME_DECAY);
        long totalElements = redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY);
        if (candidateIds.isEmpty()) {
            List<PostSummaryRes> emptyList = Collections.emptyList();
            return new PageImpl<>(Objects.requireNonNull(emptyList), Objects.requireNonNull(pageable), totalElements);
        }

        List<CheerPost> posts = postRepo.findAllByIdWithGraph(candidateIds);
        UserEntity me = current.getOrNull();
        Set<Long> excludedIds = getExcludedUserIds();

        Map<Long, CheerPost> postMap = posts.stream()
                .collect(Collectors.toMap(CheerPost::getId, Function.identity()));
        List<CheerPost> candidatePosts = candidateIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .filter(post -> !excludedIds.contains(post.getAuthor().getId()))
                .toList();

        if (candidatePosts.isEmpty()) {
            List<PostSummaryRes> emptyList = Collections.emptyList();
            return new PageImpl<>(Objects.requireNonNull(emptyList), Objects.requireNonNull(pageable), totalElements);
        }

        List<Long> candidatePostIds = candidatePosts.stream().map(CheerPost::getId).toList();
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(candidatePostIds);
        Set<Long> followingIds = me != null
                ? new HashSet<>(followService.getFollowingIds(me.getId()))
                : Collections.emptySet();
        java.time.Instant now = java.time.Instant.now();

        List<ScoredHotPost> scoredPosts = candidatePosts.stream()
                .map(post -> {
                    int combinedViews = post.getViews() + viewCountMap.getOrDefault(post.getId(), 0);
                    double globalScore = popularFeedScoringService.calculateTimeDecayScore(post, combinedViews, now);
                    double normalizedGlobal = popularFeedScoringService.normalizeGlobalHotScore(globalScore);
                    double teamAffinity = me != null
                            ? popularFeedScoringService.calculateTeamAffinity(me.getFavoriteTeamId(), post.getTeamId())
                            : 0.0;
                    double followAffinity = me != null
                            ? popularFeedScoringService.calculateFollowAffinity(followingIds, post.getAuthor().getId())
                            : 0.0;
                    double hybridScore = popularFeedScoringService.calculateHybridScore(
                            normalizedGlobal, teamAffinity, followAffinity);
                    return new ScoredHotPost(post, hybridScore);
                })
                .sorted(Comparator.comparingDouble(ScoredHotPost::score)
                        .reversed()
                        .thenComparing(scored -> scored.post().getCreatedAt(), Comparator.reverseOrder()))
                .toList();

        if (scoredPosts.isEmpty()) {
            List<PostSummaryRes> emptyList = Collections.emptyList();
            return new PageImpl<>(Objects.requireNonNull(emptyList), Objects.requireNonNull(pageable), totalElements);
        }

        int fromIndex = Math.min(start, scoredPosts.size());
        int toIndex = Math.min(end + 1, scoredPosts.size());
        List<CheerPost> pagedPosts = scoredPosts.subList(fromIndex, toIndex).stream()
                .map(ScoredHotPost::post)
                .toList();

        List<PostSummaryRes> content = toHotPostSummary(pagedPosts, me);
        return new PageImpl<>(Objects.requireNonNull(content), Objects.requireNonNull(pageable), totalElements);
    }

    private List<PostSummaryRes> toHotPostSummary(List<CheerPost> posts, UserEntity me) {
        List<Long> postIds = posts.stream().map(CheerPost::getId).toList();
        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);
        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(posts);
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = redisPostService.getCachedHotStatuses(postIds);
        Map<Long, Integer> bookmarkCountMap = getBookmarkCountMap(postIds);

        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> repostedPostIds = new HashSet<>();
        if (me != null && !postIds.isEmpty()) {
            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostRepost> reposts = repostRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            repostedPostIds = reposts.stream().map(r -> r.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalLikes = likedPostIds;
        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalReposts = repostedPostIds;

        return posts.stream()
                .map(post -> {
                    boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
                    List<String> imageUrls = imageUrlsByPostId.getOrDefault(post.getId(), Collections.emptyList());
                    return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                            finalBookmarks.contains(post.getId()), isOwner, finalReposts.contains(post.getId()),
                            bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                            viewCountMap, hotStatusMap, repostImageUrls);
                })
                .collect(Collectors.toList());
    }

    /**
     * 게시글 HOT 점수 업데이트
     * - TIME_DECAY 점수
     * - ENGAGEMENT_RATE 점수
     */
    public void updateHotScore(CheerPost post) {
        int combinedViews = getCombinedViewCount(post);
        java.time.Instant now = java.time.Instant.now();

        double timeDecayScore = popularFeedScoringService.calculateTimeDecayScore(post, combinedViews, now);
        double engagementRateScore = popularFeedScoringService.calculateEngagementRateScore(post, combinedViews);

        redisPostService.updateHotScore(post.getId(), timeDecayScore, PopularFeedAlgorithm.TIME_DECAY);
        redisPostService.updateHotScore(post.getId(), engagementRateScore, PopularFeedAlgorithm.ENGAGEMENT_RATE);
    }

    private int getCombinedViewCount(CheerPost post) {
        Integer redisViews = redisPostService.getViewCount(post.getId());
        return post.getViews() + (redisViews != null ? redisViews : 0);
    }

    private record ScoredHotPost(CheerPost post, double score) {
    }

    /**
     * 리포스트 원본 게시글의 이미지 URL을 벌크 프리페치
     */
    private Map<Long, List<String>> prefetchRepostOriginalImages(List<CheerPost> posts) {
        List<Long> repostOriginalIds = posts.stream()
                .filter(CheerPost::isRepost)
                .map(CheerPost::getRepostOf)
                .filter(Objects::nonNull)
                .map(CheerPost::getId)
                .distinct()
                .toList();
        return repostOriginalIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(repostOriginalIds);
    }

    private Map<Long, Integer> getBookmarkCountMap(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return bookmarkRepo.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        CheerBookmarkRepo.PostBookmarkCount::getPostId,
                        item -> item.getBookmarkCount().intValue()));
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

        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(page.getContent());
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = redisPostService.getCachedHotStatuses(postIds);
        Map<Long, Integer> bookmarkCountMap = getBookmarkCountMap(postIds);

        UserEntity me = current.getOrNull();
        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> repostedPostIds = new HashSet<>();
        if (me != null && !postIds.isEmpty()) {
            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostRepost> reposts = repostRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            repostedPostIds = reposts.stream().map(r -> r.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalLikes = likedPostIds;
        final Set<Long> finalReposts = repostedPostIds;
        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                    finalBookmarks.contains(post.getId()), isOwner, finalReposts.contains(post.getId()),
                    bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                    viewCountMap, hotStatusMap, repostImageUrls);
        });
    }

    /**
     * 팔로우한 유저들의 게시글 조회 (팔로우 피드)
     */
    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listFollowingPosts(Pageable pageable) {
        UserEntity me = current.get();

        // 내가 팔로우하는 유저 ID 목록
        List<Long> followingIds = followService.getFollowingIds(me.getId());
        if (followingIds.isEmpty()) {
            return new PageImpl<PostSummaryRes>(List.of(), Objects.requireNonNull(pageable), 0);
        }

        // 내가 차단한 유저 ID 목록
        List<Long> blockedIds = blockService.getBlockedIds(me.getId());

        Page<CheerPost> page;
        if (blockedIds.isEmpty()) {
            page = postRepo.findByAuthorIdIn(followingIds, pageable);
        } else {
            page = postRepo.findByAuthorIdInAndAuthorIdNotIn(followingIds, blockedIds, pageable);
        }

        List<Long> postIds = page.hasContent()
                ? page.getContent().stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(page.getContent());
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = redisPostService.getCachedHotStatuses(postIds);
        Map<Long, Integer> bookmarkCountMap = getBookmarkCountMap(postIds);

        Set<Long> bookmarkedPostIds = new HashSet<>();
        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> repostedPostIds = new HashSet<>();
        if (!postIds.isEmpty()) {
            List<CheerPostBookmark> bookmarks = bookmarkRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            bookmarkedPostIds = bookmarks.stream().map(b -> b.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostRepost> reposts = repostRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            repostedPostIds = reposts.stream().map(r -> r.getId().getPostId()).collect(Collectors.toSet());
        }

        final Set<Long> finalBookmarks = bookmarkedPostIds;
        final Set<Long> finalLikes = likedPostIds;
        final Set<Long> finalReposts = repostedPostIds;
        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            boolean isOwner = permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(post, finalLikes.contains(post.getId()),
                    finalBookmarks.contains(post.getId()), isOwner, finalReposts.contains(post.getId()),
                    bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                    viewCountMap, hotStatusMap, repostImageUrls);
        });
    }

    @Transactional
    public PostDetailRes get(Long id) {
        UserEntity me = current.getOrNull();
        CheerPost post = findPostById(id);

        // [NEW] 차단 관계 확인
        if (me != null && blockService.hasBidirectionalBlock(me.getId(), post.getAuthor().getId())) {
            throw new IllegalStateException("차단된 사용자의 게시글은 조회할 수 없습니다.");
        }

        increaseViewCount(id, post, me);

        boolean liked = me != null && isPostLikedByUser(id, me.getId());
        boolean isBookmarked = me != null && isPostBookmarkedByUser(id, me.getId());
        boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
        boolean repostedByMe = me != null && isPostRepostedByUser(id, me.getId());
        int bookmarkCount = Math.toIntExact(bookmarkRepo.countById_PostId(id));

        return postDtoMapper.toPostDetailRes(post, liked, isBookmarked, isOwner, repostedByMe, bookmarkCount);
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

    private CheerPost resolveActionTargetPost(Long postId) {
        CheerPost target = findPostById(postId);
        return resolveActionTargetPost(target);
    }

    private CheerPost resolveActionTargetPost(CheerPost post) {
        CheerPost current = post;
        int hops = 0;
        while (current.isRepost()) {
            CheerPost parent = current.getRepostOf();
            if (parent == null) {
                log.warn("resolveActionTargetPost - orphan repost detected; fallback to current row. postId={}, authorId={}, repostType={}",
                        current.getId(),
                        current.getAuthor() != null ? current.getAuthor().getId() : null,
                        current.getRepostType());
                return current;
            }
            current = parent;
            if (++hops > 32) {
                throw new IllegalArgumentException("리포스트 대상이 비정상적으로 설정되어 있습니다.");
            }
        }
        return current;
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

    private boolean isPostRepostedByUser(Long postId, Long userId) {
        return repostRepo.existsById(new CheerPostRepost.Id(postId, userId));
    }

    @Transactional
    public PostDetailRes createPost(CreatePostReq req) {
        UserEntity me = current.get();
        String normalizedTeamId = normalizeTeamId(req.teamId());
        log.debug("createPost - requested authorId={} normalizedTeamId={}", me != null ? me.getId() : null, normalizedTeamId);
        UserEntity author = resolveWriteAuthor(me);
        // 저장 직전 재검증(토큰/계정 상태 동기화 갱신)
        author = ensureAuthorRecordStillExists(author);

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService
                .checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

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
                log.warn("createPost - foreign key violation on cheer_post insert but author still valid. authorId={}, teamId={}, message={}",
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

    private UserEntity resolveWriteAuthor(UserEntity me) {
        if (me == null || me.getId() == null) {
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        Long principalUserId = getAuthenticationUserId();
        if (principalUserId != null && !principalUserId.equals(me.getId())) {
            log.warn("resolveWriteAuthor - token principal mismatch. meId={}, principalId={}", me.getId(), principalUserId);
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        log.debug("resolveWriteAuthor - loading user for write with lock. userId={}", me.getId());
        UserEntity author = userRepo.findByIdForWrite(me.getId())
                .orElseThrow(() -> new InvalidAuthorException(
                        "인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요."));
        log.debug("resolveWriteAuthor - user loaded for write. userId={}, enabled={}, locked={}", author.getId(),
                author.isEnabled(), author.isLocked());
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
        log.debug("ensureAuthorRecordStillExists - authorId={}, tokenVersion={}", author.getId(), tokenVersion);

        if (tokenVersion == null) {
            boolean hasUsableAuthor = userRepo.lockUsableAuthorForWrite(author.getId()).isPresent();
            if (!hasUsableAuthor) {
                log.warn("ensureAuthorRecordStillExists - author unusable. authorId={}, tokenVersion={}, reason=lockUsableAuthorForWrite() not found",
                        author.getId(), tokenVersion);
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
            }
        } else {
            boolean hasUsableAuthor = userRepo.lockUsableAuthorForWriteWithTokenVersion(author.getId(), tokenVersion)
                    .isPresent();
            if (!hasUsableAuthor) {
                log.warn("ensureAuthorRecordStillExists - author unusable. authorId={}, tokenVersion={}, reason=lockUsableAuthorForWriteWithTokenVersion() not found",
                        author.getId(), tokenVersion);
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
            }
        }

        UserEntity freshAuthor = userRepo.findByIdForWrite(author.getId())
                .orElseThrow(() -> new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요."));

        if (tokenVersion != null) {
            int currentTokenVersion = freshAuthor.getTokenVersion() == null ? 0 : freshAuthor.getTokenVersion();
            if (currentTokenVersion != tokenVersion) {
                log.warn("ensureAuthorRecordStillExists - tokenVersion mismatch. authorId={}, tokenVersion={}, currentTokenVersion={}",
                        freshAuthor.getId(), tokenVersion, currentTokenVersion);
                throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
            }
        }

        if (!freshAuthor.isEnabled()) {
            log.warn("ensureAuthorRecordStillExists - author disabled. authorId={}", freshAuthor.getId());
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        if (!freshAuthor.isEnabled() || !isAccountUsableForWrite(freshAuthor)) {
            log.warn("ensureAuthorRecordStillExists - author disabled/locked. authorId={}, enabled={}, locked={}, lockExpiresAt={}",
                    freshAuthor.getId(), freshAuthor.isEnabled(), freshAuthor.isLocked(), freshAuthor.getLockExpiresAt());
            throw new InvalidAuthorException("인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.");
        }

        return freshAuthor;
    }

    private Long getAuthenticationUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }

        if (principal instanceof Long userId) {
            return userId;
        }
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
        if (authentication == null) {
            return null;
        }

        Object details = authentication.getDetails();
        if (details == null) {
            return null;
        }

        if (details instanceof Integer version) {
            return version;
        }
        if (details instanceof Long version) {
            return version.intValue();
        }
        if (details instanceof Number number) {
            return number.intValue();
        }
        if (details instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignore) {
                // skip
            }
        }
        if (details instanceof Map<?, ?> map) {
            Object legacyTokenVersionValue = map.get("token_version");
            Integer legacyParsed = resolveTokenVersionFromUnknownType(legacyTokenVersionValue);
            if (legacyParsed != null) {
                return legacyParsed;
            }

            Object tokenVersionValue = map.get("tokenVersion");
            return resolveTokenVersionFromUnknownType(tokenVersionValue);
        }
        return null;
    }

    private Integer resolveTokenVersionFromUnknownType(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer version) {
            return version;
        }
        if (value instanceof Long version) {
            return version.intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String version) {
            try {
                return Integer.parseInt(version);
            } catch (NumberFormatException ignore) {
                // skip
            }
        }
        return null;
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

    private boolean isDeletedAuthorReference(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        if (!lower.contains("foreign key") || lower.contains("null value in column")) {
            return false;
        }

        boolean hasAuthorColumn = lower.contains("author_id") || lower.contains("user_id") || lower.contains("reporter_id");
        if (!hasAuthorColumn) {
            return false;
        }

        boolean targetsUserTable = lower.contains("public.users") || lower.contains("\"users\"");
        if (!targetsUserTable) {
            return false;
        }

        return lower.contains("cheer_post") || lower.contains("cheer_comment")
                || lower.contains("cheer_post_like") || lower.contains("cheer_post_bookmark")
                || lower.contains("cheer_comment_like") || lower.contains("cheer_post_repost")
                || lower.contains("cheer_post_reports");
    }

    /**
     * 새 게시글 작성 시 notify_new_posts=true 인 팔로워들에게 알림 전송
     */
    private void sendNewPostNotificationToFollowers(CheerPost post, UserEntity author) {
        try {
            List<Long> notifyUserIds = followService.getFollowersWithNotifyEnabled(author.getId());
            if (notifyUserIds.isEmpty()) {
                return;
            }

            String authorName = author.getName() != null && !author.getName().isBlank()
                    ? author.getName()
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
                    log.warn("팔로워 알림 전송 실패: userId={}, postId={}, error={}",
                            userId, post.getId(), e.getMessage());
                }
            }
            log.info("새 글 알림 전송 완료: postId={}, 알림 대상={}명", post.getId(), notifyUserIds.size());
        } catch (Exception e) {
            log.warn("팔로워 알림 전송 중 오류: postId={}, error={}", post.getId(), e.getMessage());
        }
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
    private CheerPost buildNewPost(CreatePostReq req, UserEntity author, PostType postType, String normalizedTeamId) {
        log.debug("buildNewPost - teamId={}, postType={}", normalizedTeamId, postType);

        final String finalTeamId;
        String requestTeamId = normalizedTeamId;

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
                .content(sanitizePostContent(req.content()))
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

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService
                .checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        updatePostContent(post, req);

        boolean liked = isPostLikedByUser(id, me.getId());
        boolean isBookmarked = isPostBookmarkedByUser(id, me.getId());
        boolean repostedByMe = isPostRepostedByUser(id, me.getId());
        int bookmarkCount = Math.toIntExact(bookmarkRepo.countById_PostId(id));
        return postDtoMapper.toPostDetailRes(post, liked, isBookmarked, true, repostedByMe, bookmarkCount);
    }

    /**
     * 게시글 내용 업데이트
     */
    private void updatePostContent(CheerPost post, UpdatePostReq req) {
        post.setContent(sanitizePostContent(req.content()));
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
        UserEntity me = resolveWriteAuthor(current.get());
        CheerPost post = findPostById(postId);

        // [NEW] 차단 관계 확인 (양방향)
        validateNoBlockBetween(me.getId(), post.getAuthor().getId(), "좋아요를 누를 수 없습니다.");

        // 좋아요는 모든 팀에서 허용

        CheerPostLike.Id likeId = new CheerPostLike.Id(post.getId(), me.getId());

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
                postAuthor.deductCheerPoints(1); // Entity method
                userRepo.save(postAuthor);
                log.info("Points deducted for user {}: -1 (Entity Update)", postAuthor.getId());

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
                postAuthor.addCheerPoints(1); // Entity method
                userRepo.save(postAuthor);
                log.info("Points awarded to user {}: +1 (Entity Update)", postAuthor.getId());

                // 게시글 작성자에게 알림 (본인이 아닐 때만)
                if (!postAuthor.getId().equals(me.getId())) {
                    boolean isBlocked = blockService.hasBidirectionalBlock(me.getId(), postAuthor.getId());
                    if (!isBlocked) {
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
            }
            postRepo.save(Objects.requireNonNull(post));
            updateHotScore(post);
            return new LikeToggleResponse(liked, likes);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
    }

    @Transactional
    public BookmarkResponse toggleBookmark(Long postId) {
        UserEntity me = resolveWriteAuthor(current.get());
        CheerPost target = resolveActionTargetPost(postId);
        CheerPostBookmark.Id bookmarkId = new CheerPostBookmark.Id(target.getId(), me.getId());

        boolean bookmarked;
        try {
            if (bookmarkRepo.existsById(bookmarkId)) {
                bookmarkRepo.deleteById(bookmarkId);
                bookmarked = false;
            } else {
                CheerPostBookmark bookmark = new CheerPostBookmark();
                bookmark.setId(bookmarkId);
                bookmark.setPost(target);
                bookmark.setUser(me);
                bookmarkRepo.save(Objects.requireNonNull(bookmark));
                bookmarked = true;
            }
            int count = Math.toIntExact(bookmarkRepo.countById_PostId(target.getId()));
            return new BookmarkResponse(bookmarked, count);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
    }

    /**
     * 단순 리포스트 토글 (Simple Repost)
     * - 이미 리포스트인 글은 리포스트 불가 (중첩 방지)
     * - 사용자당 원본 게시글에 대해 1개의 단순 리포스트만 가능 (토글)
     */
    @Transactional
    public RepostToggleResponse toggleRepost(Long postId) {
        UserEntity me = resolveWriteAuthor(current.get());
        CheerPost original = resolveActionTargetPost(postId);

        // 1. 차단 관계 확인 (양방향)
        if (blockService.hasBidirectionalBlock(me.getId(), original.getAuthor().getId())) {
            throw new IllegalStateException("차단된 사용자의 게시글은 리포스트할 수 없습니다.");
        }

        // 2. 비공개 계정 확인 (본인이 아닌 경우)
        if (original.getAuthor().isPrivateAccount() && !original.getAuthor().getId().equals(me.getId())) {
            throw new IllegalStateException("비공개 계정의 게시글은 리포스트할 수 없습니다.");
        }

        // 기존 단순 리포스트 확인
        java.util.Optional<CheerPost> existing = postRepo.findByAuthorAndRepostOfAndRepostType(
                me, original, CheerPost.RepostType.SIMPLE);

        boolean reposted;
        int count;

        try {
            if (existing.isPresent()) {
                // 취소: 리포스트 게시글 삭제
                postRepo.delete(Objects.requireNonNull(existing.get()));
                count = Math.max(0, original.getRepostCount() - 1);
                original.setRepostCount(count);
                reposted = false;

                // 기존 CheerPostRepost 테이블에서도 삭제 (호환성 유지)
                CheerPostRepost.Id repostTrackingId = new CheerPostRepost.Id(original.getId(), me.getId());
                if (repostRepo.existsById(repostTrackingId)) {
                    repostRepo.deleteById(repostTrackingId);
                }
            } else {
                // 생성: 새 리포스트 게시글
                CheerPost repost = CheerPost.builder()
                        .author(me)
                        .team(original.getTeam())
                        .repostOf(original)
                        .repostType(CheerPost.RepostType.SIMPLE)
                        .content(sanitizePostContent(""))
                        .postType(PostType.NORMAL)
                        .build();
                postRepo.save(Objects.requireNonNull(repost));

                count = original.getRepostCount() + 1;
                original.setRepostCount(count);
                reposted = true;

                // 기존 CheerPostRepost 테이블에도 추가 (호환성 유지 - repostedByMe 조회용)
                CheerPostRepost.Id repostTrackingId = new CheerPostRepost.Id(original.getId(), me.getId());
                if (!repostRepo.existsById(repostTrackingId)) {
                    CheerPostRepost repostTracking = new CheerPostRepost();
                    repostTracking.setId(repostTrackingId);
                    repostTracking.setPost(original);
                    repostTracking.setUser(me);
                    repostRepo.save(repostTracking);
                }

                // 알림 (본인 글 제외)
                if (!original.getAuthor().getId().equals(me.getId())) {
                    try {
                        String authorName = me.getName() != null && !me.getName().isBlank()
                                ? me.getName()
                                : me.getEmail();

                        notificationService.createNotification(
                                Objects.requireNonNull(original.getAuthor().getId()),
                                com.example.notification.entity.Notification.NotificationType.POST_REPOST,
                                "리포스트 알림",
                                authorName + "님이 회원님의 게시글을 리포스트했습니다.",
                                original.getId());
                    } catch (Exception e) {
                        log.warn("리포스트 알림 생성 실패: postId={}, error={}", original.getId(), e.getMessage());
                    }
                }
            }
            postRepo.save(Objects.requireNonNull(original));
            updateHotScore(original);
            return new RepostToggleResponse(reposted, count);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
    }

    /**
     * 인용 리포스트 생성 (Quote Repost)
     * - 원글을 첨부하면서 의견(코멘트)을 덧붙여 작성
     * - 여러 번 가능 (토글 아님)
     * - 이미 리포스트인 글은 인용 불가 (중첩 방지)
     */
    @Transactional
    public PostDetailRes createQuoteRepost(Long originalPostId, QuoteRepostReq req) {
        UserEntity me = resolveWriteAuthor(current.get());
        CheerPost original = findPostById(originalPostId);

        // 중첩 방지: 이미 리포스트인 글은 인용 불가
        if (original.isRepost()) {
            throw new IllegalArgumentException("리포스트된 글은 인용할 수 없습니다.");
        }

        // 1. 차단 관계 확인 (양방향)
        if (blockService.hasBidirectionalBlock(me.getId(), original.getAuthor().getId())) {
            throw new IllegalStateException("차단된 사용자의 게시글은 리포스트할 수 없습니다.");
        }

        // 2. 비공개 계정 확인 (본인이 아닌 경우)
        if (original.getAuthor().isPrivateAccount() && !original.getAuthor().getId().equals(me.getId())) {
            throw new IllegalStateException("비공개 계정의 게시글은 리포스트할 수 없습니다.");
        }

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        try {
            CheerPost quoteRepost = CheerPost.builder()
                    .author(me)
                    .team(original.getTeam())
                    .repostOf(original)
                    .repostType(CheerPost.RepostType.QUOTE)
                    // title removed
                    .content(sanitizePostContent(req.content())) // 사용자가 작성한 의견
                    .postType(PostType.NORMAL)
                    .build();
            postRepo.save(Objects.requireNonNull(quoteRepost));

            original.setRepostCount(original.getRepostCount() + 1);
            postRepo.save(original);

            updateHotScore(original);

            // 알림 (본인 글 제외)
            if (!original.getAuthor().getId().equals(me.getId())) {
                try {
                    String authorName = me.getName() != null && !me.getName().isBlank()
                            ? me.getName()
                            : me.getEmail();

                    notificationService.createNotification(
                            Objects.requireNonNull(original.getAuthor().getId()),
                            com.example.notification.entity.Notification.NotificationType.POST_REPOST,
                            "인용 리포스트",
                            authorName + "님이 회원님의 게시글을 인용했습니다.",
                            quoteRepost.getId());
                } catch (Exception e) {
                    log.warn("인용 리포스트 알림 생성 실패: originalPostId={}, error={}", originalPostId, e.getMessage());
                }
            }

            return postDtoMapper.toNewPostDetailRes(quoteRepost, me);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
    }

    private String sanitizePostContent(String content) {
        return (content == null || content.isBlank()) ? "" : content;
    }

    @Transactional
    public void reportPost(Long postId, ReportRequest req) {
        UserEntity reporter = resolveWriteAuthor(current.get());
        CheerPost post = findPostById(postId);

        CheerPostReport report = CheerPostReport.builder()
                .post(post)
                .reporter(reporter)
                .reason(req.reason())
                .description(req.description())
                .build();

        try {
            reportRepo.save(Objects.requireNonNull(report));
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(reporter);
            }
            throw ex;
        }
    }

    /**
     * 리포스트 취소 (단순 리포스트 삭제 및 원본 게시글 카운트 업데이트)
     * - 사용자가 작성한 리포스트 게시글 삭제
     * - 원본 게시글의 repostCount 감소
     * - CheerPostRepost 테이블에서도 삭제
     * - 원본 게시글 ID와 업데이트된 리포스트 수 반환
     */
    @Transactional
    public RepostToggleResponse cancelRepost(Long repostId) {
        UserEntity me = resolveWriteAuthor(current.get());

        try {
            CheerPost repost = findPostById(repostId);

            if (!repost.isRepost()) {
                throw new IllegalArgumentException("리포스트가 아닌 게시글은 취소할 수 없습니다.");
            }

            if (!repost.getAuthor().getId().equals(me.getId())) {
                throw new IllegalStateException("자신의 리포스트만 취소할 수 있습니다.");
            }

            CheerPost original = repost.getRepostOf();
            if (original == null) {
                throw new IllegalStateException("원본 게시글을 찾을 수 없습니다.");
            }

            original.setRepostCount(Math.max(0, original.getRepostCount() - 1));
            postRepo.save(Objects.requireNonNull(original));

            postRepo.delete(repost);

            CheerPostRepost.Id repostTrackingId = new CheerPostRepost.Id(original.getId(), me.getId());
            if (repostRepo.existsById(repostTrackingId)) {
                repostRepo.deleteById(repostTrackingId);
            }

            return new RepostToggleResponse(false, original.getRepostCount());
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
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

        List<CheerPost> bookmarkedPosts = bookmarks.getContent().stream()
                .map(CheerPostBookmark::getPost).toList();
        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(bookmarkedPosts);
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = redisPostService.getCachedHotStatuses(postIds);
        Map<Long, Integer> bookmarkCountMap = getBookmarkCountMap(postIds);

        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> repostedPostIds = new HashSet<>();
        if (!postIds.isEmpty()) {
            List<CheerPostLike> likes = likeRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            likedPostIds = likes.stream().map(l -> l.getId().getPostId()).collect(Collectors.toSet());

            List<CheerPostRepost> reposts = repostRepo.findByUserIdAndPostIdIn(me.getId(), postIds);
            repostedPostIds = reposts.stream().map(r -> r.getId().getPostId()).collect(Collectors.toSet());
        }
        final Set<Long> finalLikes = likedPostIds;
        final Set<Long> finalReposts = repostedPostIds;

        return bookmarks.map(b -> {
            boolean isOwner = permissionValidator.isOwnerOrAdmin(me, b.getPost().getAuthor());
            List<String> imageUrls = finalImageUrls.getOrDefault(b.getPost().getId(), Collections.emptyList());
            return postDtoMapper.toPostSummaryRes(b.getPost(), finalLikes.contains(b.getPost().getId()), true, isOwner,
                    finalReposts.contains(b.getPost().getId()), bookmarkCountMap.getOrDefault(b.getPost().getId(), 0),
                    imageUrls,
                    viewCountMap, hotStatusMap, repostImageUrls);
        });
    }

    @Transactional(readOnly = true)
    public Page<CommentRes> listComments(Long postId, Pageable pageable) {
        // Use the optimized query to fetch comments and replies in one go
        List<CheerComment> allComments = commentRepo.findCommentsWithRepliesByPostId(Objects.requireNonNull(postId));

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allComments.size());

        if (start > allComments.size()) {
            return new PageImpl<>(List.of(), pageable, allComments.size());
        }

        List<CheerComment> pagedComments = allComments.subList(start, end);

        UserEntity me = current.getOrNull();
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
                .map(comment -> toCommentResWithLikedSet(comment, finalLikedIds))
                .toList();
        return new PageImpl<>(mapped, pageable, allComments.size());
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
        UserEntity me = resolveWriteAuthor(current.get());
        CheerPost post = resolveActionTargetPost(postId);

        // [NEW] 차단 관계 확인
        validateNoBlockBetween(me.getId(), post.getAuthor().getId(), "차단 관계가 있어 댓글을 작성할 수 없습니다.");

        permissionValidator.validateTeamAccess(me, post.getTeamId(), "댓글 작성");

        // AI Moderation 체크
        AIModerationService.ModerationResult modResult = moderationService.checkContent(req.content());
        if (!modResult.isAllowed()) {
            throw new IllegalArgumentException("부적절한 내용이 포함되어 있습니다: " + modResult.reason());
        }

        try {
            // 중복 댓글 체크: 직전 3초 이내 동일 작성자·게시글·내용 댓글 확인
            checkDuplicateComment(post.getId(), me.getId(), req.content(), null);

            CheerComment comment = saveNewComment(post, me, req);
            incrementCommentCount(post);
            updateHotScore(post);

            // 게시글 작성자에게 알림 (본인이 아닐 때만)
            if (!post.getAuthor().getId().equals(me.getId())) {
                boolean isBlocked = blockService.hasBidirectionalBlock(me.getId(), post.getAuthor().getId());
                if (!isBlocked) {
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
            }

            return toCommentRes(comment);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
    }

    @Transactional
    public void deleteComment(Long commentId) {
        UserEntity me = resolveWriteAuthor(current.get());
        CheerComment comment = findCommentById(commentId);
        permissionValidator.validateOwnerOrAdmin(me, comment.getAuthor(), "댓글 삭제");

        CheerPost post = comment.getPost();
        try {
            commentRepo.delete(comment);

            // 실제 DB에서 댓글 수 재계산 (댓글 + 대댓글 모두 포함)
            // Null type safety 해결을 위해 primitive type 변환 후 전달
            Long actualCount = commentRepo.countByPostId(Objects.requireNonNull(post.getId()).longValue());
            post.setCommentCount(actualCount != null ? actualCount.intValue() : 0);
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
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
                resolveAuthorProfileImageUrl(comment.getAuthor()),
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
                resolveAuthorProfileImageUrl(comment.getAuthor()),
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

    private String resolveAuthorProfileImageUrl(UserEntity author) {
        if (author == null) {
            return null;
        }

        String rawValue = author.getProfileImageUrl();
        String resolved = profileImageService.getProfileImageUrl(rawValue);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }

        if (rawValue != null && !rawValue.isBlank()) {
            if (rawValue.startsWith("http://")
                    || rawValue.startsWith("https://")
                    || rawValue.startsWith("/")) {
                return rawValue;
            }
        }

        return null;
    }

    /**
     * 댓글 좋아요 토글
     */
    @Transactional
    public LikeToggleResponse toggleCommentLike(Long commentId) {
        UserEntity me = resolveWriteAuthor(current.get());
        CheerComment comment = findCommentById(commentId);

        // 댓글이 속한 게시글의 팀 권한 확인
        permissionValidator.validateTeamAccess(me, comment.getPost().getTeamId(), "댓글 좋아요");

        CheerCommentLike.Id likeId = new CheerCommentLike.Id(comment.getId(), me.getId());

        boolean liked;
        int likes;

        try {
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
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
    }

    /**
     * 대댓글 작성
     */
    @Transactional
    public CommentRes addReply(Long postId, Long parentCommentId, CreateCommentReq req) {
        UserEntity me = resolveWriteAuthor(current.get());
        CheerPost post = findPostById(postId);
        CheerComment parentComment = findCommentById(parentCommentId);

        // [NEW] 차단 관계 확인
        validateNoBlockBetween(me.getId(), post.getAuthor().getId(), "원글 작성자와 차단 관계가 있어 답글을 작성할 수 없습니다.");
        validateNoBlockBetween(me.getId(), parentComment.getAuthor().getId(), "댓글 작성자와 차단 관계가 있어 답글을 작성할 수 없습니다.");

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

        try {
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
        } catch (DataIntegrityViolationException ex) {
            if (isDeletedAuthorReference(ex)) {
                ensureAuthorRecordStillExists(me);
            }
            throw ex;
        }
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

    /**
     * 경량 게시글 목록 조회 (최소 데이터만 포함)
     * - 페이로드 최소화를 위한 경량 버전
     * - summary=true 파라미터 사용 시 호출
     */
    @Transactional(readOnly = true)
    public Page<PostLightweightSummaryRes> listLightweight(String teamId, String postTypeStr, Pageable pageable) {
        String normalizedTeamId = normalizeTeamId(teamId);
        if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
            UserEntity me = current.getOrNull();
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
        }

        // PostType 필터링 적용
        PostType postType = null;
        if (postTypeStr != null && !postTypeStr.isBlank()) {
            try {
                postType = PostType.valueOf(postTypeStr);
            } catch (IllegalArgumentException e) {
                // 무시하고 전체 조회
            }
        }

        Page<CheerPost> page;
        boolean hasSort = pageable.getSort().isSorted();

        // [NEW] 차단 유저 ID 목록
        java.util.Set<Long> excludedIds = getExcludedUserIds();

        if (hasSort && pageable.getSort().stream().anyMatch(order -> !order.getProperty().equals("createdAt"))) {
            page = postRepo.findByTeamIdAndPostType(normalizedTeamId, postType, excludedIds, pageable);
        } else {
            java.time.Instant cutoffDate = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
            page = postRepo.findAllOrderByPostTypeAndCreatedAt(normalizedTeamId, postType, cutoffDate, excludedIds,
                    pageable);
        }

        List<Long> postIds = page.hasContent()
                ? page.getContent().stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        final Map<Long, List<String>> finalImageUrls = imageUrlsByPostId;

        return page.map(post -> {
            List<String> imageUrls = finalImageUrls.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostLightweightSummaryRes(post, imageUrls);
        });
    }

    /**
     * 새 게시글 변경사항 체크 (폴링용 경량 엔드포인트)
     * - 특정 ID 이후의 새 게시글 수와 최신 ID만 반환
     * - 최소 데이터 전송으로 효율적인 폴링 지원
     */
    @Transactional(readOnly = true)
    public PostChangesResponse checkPostChanges(Long sinceId, String teamId) {
        String normalizedTeamId = normalizeTeamId(teamId);
        if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
            UserEntity me = current.getOrNull();
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
        }

        int newCount = postRepo.countNewPostsSince(sinceId != null ? sinceId : 0L, normalizedTeamId);
        Long latestId = postRepo.findLatestPostId(normalizedTeamId);

        return new PostChangesResponse(newCount, latestId);
    }

    /**
     * [NEW] 차단 등으로 제외해야 할 유저 ID 목록 조회
     */
    private java.util.Set<Long> getExcludedUserIds() {
        UserEntity me = current.getOrNull();
        if (me == null)
            return java.util.Collections.emptySet();

        java.util.Set<Long> excluded = new java.util.HashSet<>();
        excluded.addAll(blockService.getBlockedIds(me.getId()));
        excluded.addAll(blockService.getBlockerIds(me.getId()));
        return excluded;
    }

    /**
     * [NEW] 차단 관계 확인 및 예외 발생
     */
    private void validateNoBlockBetween(Long user1, Long user2, String message) {
        if (blockService.hasBidirectionalBlock(user1, user2)) {
            throw new IllegalStateException(message);
        }
    }
}
