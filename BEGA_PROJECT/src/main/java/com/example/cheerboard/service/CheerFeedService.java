package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostChangesResponse;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.profile.storage.service.ProfileImageService;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserBlock;
import com.example.auth.entity.UserFollow;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.service.UserService;
import com.example.kbo.util.TeamCodeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerFeedService {

    private final CheerPostRepo postRepo;
    private final CheerInteractionService interactionService;
    private final ImageService imageService;
    private final RedisPostService redisPostService;
    private final PopularFeedScoringService popularFeedScoringService;
    private final FollowService followService;
    private final BlockService blockService;
    private final PublicVisibilityVerifier publicVisibilityVerifier;
    private final UserService userService;
    private final PermissionValidator permissionValidator;
    private final PostDtoMapper postDtoMapper;
    private final ProfileImageService profileImageService;
    private final com.example.cheerboard.repo.CheerBookmarkRepo bookmarkRepo;

    // We need to resolve Normalized Team ID, so call utility directly or via helper

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getBookmarkedPosts(Pageable pageable, UserEntity me) {
        if (me == null) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }

        Page<com.example.cheerboard.domain.CheerPostBookmark> bookmarkPage = bookmarkRepo
                .findVisibleByUserIdOrderByCreatedAtDesc(me.getId(), me.getId(), pageable);

        Page<CheerPost> postPage = bookmarkPage.map(com.example.cheerboard.domain.CheerPostBookmark::getPost);
        return buildPostSummaryPage(postPage, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> list(String teamId, String postTypeStr, Pageable pageable, UserEntity me) {
        String normalizedTeamId = resolveTeamFilter(teamId);

        if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
        }

        PostType postType = null;
        if (postTypeStr != null && !postTypeStr.isBlank()) {
            try {
                postType = PostType.valueOf(postTypeStr);
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }

        Page<CheerPost> page = findVisibleFeedPage(normalizedTeamId, postType, pageable, me);
        return buildPostSummaryPage(page, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> search(String q, String teamId, Pageable pageable, UserEntity me) {
        String normalizedTeamId = resolveTeamFilter(teamId);
        Specification<CheerPost> spec = notSimpleRepost()
                .and(teamMatches(normalizedTeamId))
                .and(contentContains(q))
                .and(visibleToViewer(me));
        Page<CheerPost> page = postRepo.findAll(spec, pageable);

        return buildPostSummaryPage(page, me);
    }

    @Transactional(readOnly = true)
    public Page<PostLightweightSummaryRes> listLightweight(String teamId, String postTypeStr, Pageable pageable,
            UserEntity me) {
        String normalizedTeamId = resolveTeamFilter(teamId);
        if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
        }

        PostType postType = null;
        if (postTypeStr != null && !postTypeStr.isBlank()) {
            try {
                postType = PostType.valueOf(postTypeStr);
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }

        Page<CheerPost> page = findVisibleFeedPage(normalizedTeamId, postType, pageable, me);
        List<CheerPost> visiblePosts = filterPostsWithAuthor(page.getContent());

        List<Long> postIds = !visiblePosts.isEmpty()
                ? visiblePosts.stream().map(CheerPost::getId).toList()
                : Collections.emptyList();

        Map<Long, List<String>> imageUrlsByPostId = safeGetPostImageUrls(postIds);

        List<PostLightweightSummaryRes> content = visiblePosts.stream().map(post -> {
            try {
                List<String> imageUrls = imageUrlsByPostId.getOrDefault(post.getId(), Collections.emptyList());
                return postDtoMapper.toPostLightweightSummaryRes(post, imageUrls);
            } catch (Exception e) {
                log.warn("Cheer lightweight feed enrichment failed for postId={}, fallback to minimal response",
                        post.getId(), e);
                return buildFallbackPostLightweightSummary(post);
            }
        }).toList();

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    private com.example.cheerboard.dto.PostLightweightSummaryRes buildFallbackPostLightweightSummary(CheerPost post) {
        return com.example.cheerboard.dto.PostLightweightSummaryRes.of(
                post.getId(),
                post.getContent(),
                null,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt(),
                resolveDisplayName(post.getAuthor()),
                post.getAuthor() != null ? resolveAuthorProfileImageUrl(post.getAuthor()) : null);
    }

    @Transactional(readOnly = true)
    public PostChangesResponse checkPostChanges(Long sinceId, String teamId, UserEntity me) {
        String normalizedTeamId = resolveTeamFilter(teamId);
        if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
        }

        long resolvedSinceId = sinceId != null ? sinceId : 0L;
        List<CheerPost> visibleNewPosts = filterVisiblePosts(
                postRepo.findNewPostsSinceOrderByIdDesc(resolvedSinceId, normalizedTeamId),
                me);
        int newCount = visibleNewPosts.size();
        Long latestId = visibleNewPosts.stream()
                .map(CheerPost::getId)
                .max(Long::compareTo)
                .orElse(sinceId);

        return new PostChangesResponse(newCount, latestId);
    }

    private String resolveTeamFilter(String teamId) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);
        if (normalizedTeamId == null || normalizedTeamId.isBlank()) {
            return null;
        }
        if ("ALL".equalsIgnoreCase(normalizedTeamId)) {
            return null;
        }
        return normalizedTeamId;
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listFollowingPosts(Pageable pageable, UserEntity me) {
        if (me == null) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }

        List<Long> followingIds = followService.getFollowingIds(me.getId());
        if (followingIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Specification<CheerPost> spec = authorIdIn(followingIds)
                .and(visibleToViewer(me));
        Page<CheerPost> page = postRepo.findAll(spec, pageable);
        return buildPostSummaryPage(page, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listByUserHandle(String handle, Pageable pageable, UserEntity me) {
        Long viewerId = me != null ? me.getId() : null;
        String normalizedHandle = userService.getPublicUserProfileByHandle(handle, viewerId).getHandle();
        Specification<CheerPost> spec = authorHandleMatches(normalizedHandle)
                .and(visibleToViewer(me));
        Page<CheerPost> page = postRepo.findAll(spec, pageable);
        return buildPostSummaryPage(page, me);
    }

    private Page<CheerPost> findVisibleFeedPage(String normalizedTeamId, PostType postType, Pageable pageable, UserEntity me) {
        Specification<CheerPost> spec = notSimpleRepost()
                .and(teamMatches(normalizedTeamId))
                .and(postTypeMatches(postType))
                .and(visibleToViewer(me));

        if (usesCustomFeedSort(pageable)) {
            return postRepo.findAll(spec, pageable);
        }

        java.time.Instant cutoffDate = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
        Pageable orderManagedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return postRepo.findAll(spec.and(defaultFeedOrder(cutoffDate)), orderManagedPageable);
    }

    private boolean usesCustomFeedSort(Pageable pageable) {
        return pageable.getSort().isSorted()
                && pageable.getSort().stream().anyMatch(order -> !order.getProperty().equals("createdAt"));
    }

    private Specification<CheerPost> defaultFeedOrder(java.time.Instant cutoffDate) {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                query.orderBy(
                        cb.asc(cb.selectCase()
                                .when(
                                        cb.and(
                                                cb.equal(root.get("postType"), PostType.NOTICE),
                                                cb.greaterThan(root.get("createdAt"), cutoffDate)),
                                        0)
                                .otherwise(1)),
                        cb.desc(root.get("createdAt")));
            }
            return cb.conjunction();
        };
    }

    private Specification<CheerPost> notSimpleRepost() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("repostType")),
                cb.notEqual(root.get("repostType"), CheerPost.RepostType.SIMPLE));
    }

    private Specification<CheerPost> teamMatches(String normalizedTeamId) {
        return (root, query, cb) -> normalizedTeamId == null
                ? cb.conjunction()
                : cb.equal(root.get("team").get("teamId"), normalizedTeamId);
    }

    private Specification<CheerPost> postTypeMatches(PostType postType) {
        return (root, query, cb) -> postType == null
                ? cb.conjunction()
                : cb.equal(root.get("postType"), postType);
    }

    private Specification<CheerPost> contentContains(String queryText) {
        return (root, query, cb) -> {
            if (queryText == null || queryText.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(
                    cb.lower(root.get("content").as(String.class)),
                    "%" + queryText.trim().toLowerCase() + "%");
        };
    }

    private Specification<CheerPost> authorHandleMatches(String handle) {
        return (root, query, cb) -> cb.equal(root.get("author").get("handle"), handle);
    }

    private Specification<CheerPost> authorIdIn(List<Long> authorIds) {
        return (root, query, cb) -> authorIds == null || authorIds.isEmpty()
                ? cb.disjunction()
                : root.get("author").get("id").in(authorIds);
    }

    private Specification<CheerPost> visibleToViewer(UserEntity me) {
        Long viewerId = me != null ? me.getId() : null;
        return (root, query, cb) -> {
            if (viewerId == null) {
                return cb.equal(root.get("author").get("privateAccount").as(Boolean.class), Boolean.FALSE);
            }

            var authorId = root.get("author").get("id");
            var authorPrivate = root.get("author").get("privateAccount").as(Boolean.class);

            var blockSubquery = query.subquery(Integer.class);
            var blockRoot = blockSubquery.from(UserBlock.class);
            blockSubquery.select(cb.literal(1));
            blockSubquery.where(
                    cb.or(
                            cb.and(
                                    cb.equal(blockRoot.get("id").get("blockerId"), viewerId),
                                    cb.equal(blockRoot.get("id").get("blockedId"), authorId)),
                            cb.and(
                                    cb.equal(blockRoot.get("id").get("blockerId"), authorId),
                                    cb.equal(blockRoot.get("id").get("blockedId"), viewerId))));

            var followSubquery = query.subquery(Integer.class);
            var followRoot = followSubquery.from(UserFollow.class);
            followSubquery.select(cb.literal(1));
            followSubquery.where(
                    cb.equal(followRoot.get("id").get("followerId"), viewerId),
                    cb.equal(followRoot.get("id").get("followingId"), authorId));

            return cb.or(
                    cb.equal(authorId, viewerId),
                    cb.and(
                            cb.not(cb.exists(blockSubquery)),
                            cb.or(
                                    cb.equal(authorPrivate, Boolean.FALSE),
                                    cb.exists(followSubquery))));
        };
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getHotPosts(Pageable pageable, String algorithmRaw, UserEntity me) {
        PopularFeedAlgorithm algorithm = PopularFeedAlgorithm.from(algorithmRaw);
        if (algorithm == PopularFeedAlgorithm.HYBRID) {
            return getHybridHotPosts(pageable, me);
        }
        return getGlobalHotPosts(pageable, algorithm, me);
    }

    // --- Hot Post Logic ---

    private Page<PostSummaryRes> getGlobalHotPosts(Pageable pageable, PopularFeedAlgorithm algorithm, UserEntity me) {
        int start = (int) pageable.getOffset();
        int end = start + pageable.getPageSize() - 1;
        long totalElements = redisPostService.getHotListSize(algorithm);

        Set<Long> hotPostIds = redisPostService.getHotPostIds(start, end, algorithm);
        if (hotPostIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, totalElements);
        }

        List<CheerPost> posts = postRepo.findAllByIdWithGraph(hotPostIds);
        Map<Long, CheerPost> postMap = posts.stream()
                .collect(Collectors.toMap(CheerPost::getId, Function.identity()));
        List<CheerPost> orderedPosts = hotPostIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .toList();
        List<CheerPost> visibleOrderedPosts = me == null ? filterVisiblePosts(orderedPosts, null) : orderedPosts;
        totalElements = pruneExplicitHotEntries(
                algorithm,
                totalElements,
                orderedPosts.stream()
                        .map(CheerPost::getId)
                        .filter(id -> visibleOrderedPosts.stream().noneMatch(post -> Objects.equals(post.getId(), id)))
                        .toList());

        Map<Long, Integer> viewCountMap = safeGetViewCounts(visibleOrderedPosts.stream().map(CheerPost::getId).toList());
        java.time.Instant now = java.time.Instant.now();
        Map<Long, Boolean> eligibilityMap = visibleOrderedPosts.stream()
                .collect(Collectors.toMap(
                        CheerPost::getId,
                        post -> popularFeedScoringService.isHotEligible(
                                post,
                                post.getViews() + viewCountMap.getOrDefault(post.getId(), 0),
                                now)));
        totalElements = pruneInvalidHotEntries(
                algorithm,
                totalElements,
                hotPostIds,
                visibleOrderedPosts,
                eligibilityMap);

        // 차단 필터링
        java.util.Set<Long> excludedIds = getExcludedUserIds(me);

        // Redis 순서 유지 정렬
        List<CheerPost> sortedPosts = visibleOrderedPosts.stream()
                .filter(post -> !excludedIds.contains(post.getAuthor().getId()))
                .toList();

        List<CheerPost> eligiblePosts = sortedPosts.stream()
                .filter(post -> eligibilityMap.getOrDefault(post.getId(), false))
                .toList();

        List<PostSummaryRes> content = toHotPostSummary(eligiblePosts, me);
        return new PageImpl<>(Objects.requireNonNull(content), pageable, totalElements);
    }

    private Page<PostSummaryRes> getHybridHotPosts(Pageable pageable, UserEntity me) {
        int start = (int) pageable.getOffset();
        int end = start + pageable.getPageSize() - 1;
        int candidateEnd = Math.min(999, end + 200);

        Set<Long> candidateIds = redisPostService.getHotPostIds(0, candidateEnd, PopularFeedAlgorithm.TIME_DECAY);
        long totalElements = redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY);
        if (candidateIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, totalElements);
        }

        List<CheerPost> posts = postRepo.findAllByIdWithGraph(candidateIds);
        Set<Long> excludedIds = getExcludedUserIds(me);

        Map<Long, CheerPost> postMap = posts.stream()
                .collect(Collectors.toMap(CheerPost::getId, Function.identity()));
        List<CheerPost> orderedCandidatePosts = candidateIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .toList();
        totalElements = pruneExplicitHotEntries(
                PopularFeedAlgorithm.TIME_DECAY,
                totalElements,
                candidateIds.stream()
                        .filter(id -> orderedCandidatePosts.stream().noneMatch(post -> Objects.equals(post.getId(), id)))
                        .toList());
        List<CheerPost> visibleCandidatePosts = me == null ? filterVisiblePosts(orderedCandidatePosts, null)
                : orderedCandidatePosts;
        totalElements = pruneExplicitHotEntries(
                PopularFeedAlgorithm.TIME_DECAY,
                totalElements,
                orderedCandidatePosts.stream()
                        .map(CheerPost::getId)
                        .filter(id -> visibleCandidatePosts.stream().noneMatch(post -> Objects.equals(post.getId(), id)))
                        .toList());
        List<CheerPost> candidatePosts = visibleCandidatePosts.stream()
                .filter(post -> !excludedIds.contains(post.getAuthor().getId()))
                .toList();

        if (candidatePosts.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, totalElements);
        }

        List<Long> candidatePostIds = visibleCandidatePosts.stream().map(CheerPost::getId).toList();
        Map<Long, Integer> viewCountMap = safeGetViewCounts(candidatePostIds);
        Set<Long> followingIds = me != null
                ? new HashSet<>(followService.getFollowingIds(me.getId()))
                : Collections.emptySet();
        java.time.Instant now = java.time.Instant.now();
        Map<Long, Boolean> eligibilityMap = visibleCandidatePosts.stream()
                .collect(Collectors.toMap(
                        CheerPost::getId,
                        post -> popularFeedScoringService.isHotEligible(
                                post,
                                post.getViews() + viewCountMap.getOrDefault(post.getId(), 0),
                                now)));
        totalElements = pruneInvalidHotEntries(
                PopularFeedAlgorithm.TIME_DECAY,
                totalElements,
                candidateIds,
                visibleCandidatePosts,
                eligibilityMap);
        List<CheerPost> eligibleCandidates = candidatePosts.stream()
                .filter(post -> eligibilityMap.getOrDefault(post.getId(), false))
                .toList();

        List<ScoredHotPost> scoredPosts = eligibleCandidates.stream()
                .map(post -> {
                    int combinedViews = post.getViews() + viewCountMap.getOrDefault(post.getId(), 0);
                    double globalScore = popularFeedScoringService.calculateTimeDecayScore(post, combinedViews, now);
                    double normalizedGlobal = popularFeedScoringService.normalizeGlobalHotScore(globalScore);
                    double engagementScore = popularFeedScoringService.calculateEngagementRateScore(post,
                            combinedViews);
                    double normalizedEngagement = popularFeedScoringService
                            .normalizeGlobalHotScore(engagementScore * 100);
                    double teamAffinity = me != null
                            ? popularFeedScoringService.calculateTeamAffinity(me.getFavoriteTeamId(), post.getTeamId())
                            : popularFeedScoringService.calculateTeamAffinity(null, post.getTeamId());
                    double followAffinity = me != null
                            ? popularFeedScoringService.calculateFollowAffinity(followingIds, post.getAuthor().getId())
                            : 0.0;
                    double freshnessBoost = popularFeedScoringService.calculateFreshnessBoost(post.getCreatedAt(), now);
                    double hybridScore = popularFeedScoringService.calculateHybridScore(
                            normalizedGlobal, normalizedEngagement, teamAffinity, followAffinity, freshnessBoost);
                    return new ScoredHotPost(post, hybridScore);
                })
                .sorted(Comparator.comparingDouble(ScoredHotPost::score)
                        .reversed()
                        .thenComparing(scored -> scored.post().getCreatedAt(), Comparator.reverseOrder()))
                .toList();

        if (scoredPosts.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, totalElements);
        }

        int fromIndex = Math.min(start, scoredPosts.size());
        int toIndex = Math.min(end + 1, scoredPosts.size());
        if (fromIndex >= toIndex) { // Handle case where start is beyond size due to filtering
            return new PageImpl<>(Collections.emptyList(), pageable, totalElements);
        }

        List<CheerPost> pagedPosts = scoredPosts.subList(fromIndex, toIndex).stream()
                .map(ScoredHotPost::post)
                .toList();

        List<PostSummaryRes> content = toHotPostSummary(pagedPosts, me);
        return new PageImpl<>(Objects.requireNonNull(content), pageable, totalElements);
    }

    private long pruneInvalidHotEntries(
            PopularFeedAlgorithm algorithm,
            long currentTotalElements,
            Set<Long> requestedIds,
            List<CheerPost> resolvedPosts,
            Map<Long, Boolean> eligibilityMap) {
        LinkedHashSet<Long> invalidIds = new LinkedHashSet<>();
        Set<Long> resolvedIds = resolvedPosts.stream()
                .map(CheerPost::getId)
                .collect(Collectors.toSet());

        requestedIds.stream()
                .filter(id -> !resolvedIds.contains(id))
                .forEach(invalidIds::add);

        resolvedPosts.stream()
                .map(CheerPost::getId)
                .filter(id -> !eligibilityMap.getOrDefault(id, false))
                .forEach(invalidIds::add);

        if (invalidIds.isEmpty()) {
            return currentTotalElements;
        }

        invalidIds.forEach(redisPostService::removeFromHotList);
        return redisPostService.getHotListSize(algorithm);
    }

    private long pruneExplicitHotEntries(
            PopularFeedAlgorithm algorithm,
            long currentTotalElements,
            List<Long> invalidIds) {
        if (invalidIds == null || invalidIds.isEmpty()) {
            return currentTotalElements;
        }
        invalidIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(redisPostService::removeFromHotList);
        return redisPostService.getHotListSize(algorithm);
    }

    private List<PostSummaryRes> toHotPostSummary(List<CheerPost> posts, UserEntity me) {
        if (posts.isEmpty())
            return Collections.emptyList();
        return mapPostSummaries(filterVisiblePosts(posts, me), me);
    }

    // --- Helpers ---

    private Page<PostSummaryRes> buildPostSummaryPage(Page<CheerPost> page, UserEntity me) {
        if (!page.hasContent()) {
            return new PageImpl<>(Collections.emptyList(), page.getPageable(), page.getTotalElements());
        }
        List<PostSummaryRes> content = mapPostSummaries(page.getContent(), me);
        return new PageImpl<>(Objects.requireNonNull(content), page.getPageable(), page.getTotalElements());
    }

    private List<PostSummaryRes> mapPostSummaries(List<CheerPost> posts, UserEntity me) {
        List<CheerPost> mappedPosts = filterPostsWithAuthor(posts);
        if (mappedPosts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> postIds = mappedPosts.stream().map(CheerPost::getId).toList();

        Map<Long, List<String>> imageUrlsByPostId = safeGetPostImageUrls(postIds);
        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(mappedPosts);
        Map<Long, Integer> viewCountMap = safeGetViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = safeGetHotStatusMap(postIds);
        // Use InteractionService for counts and statuses
        Map<Long, Integer> bookmarkCountMap = safeGetBookmarkCountMap(postIds);

        Set<Long> likedPostIds = (me != null) ? safeGetLikedPostIds(me, postIds) : Collections.emptySet();
        Set<Long> bookmarkedPostIds = (me != null) ? safeGetBookmarkedPostIds(me, postIds) : Collections.emptySet();
        Set<Long> repostedPostIds = (me != null) ? safeGetRepostedPostIds(me, postIds) : Collections.emptySet();

        return mappedPosts.stream()
                .map(post -> {
                    try {
                        boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
                        List<String> imageUrls = imageUrlsByPostId.getOrDefault(post.getId(), Collections.emptyList());
                        return postDtoMapper.toPostSummaryRes(post, likedPostIds.contains(post.getId()),
                                bookmarkedPostIds.contains(post.getId()), isOwner,
                                repostedPostIds.contains(post.getId()),
                                bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                                viewCountMap, hotStatusMap, repostImageUrls);
                    } catch (Exception e) {
                        log.warn("Cheer feed summary enrichment failed for postId={}, fallback to minimal response",
                                post.getId(), e);
                        return buildFallbackPostSummary(post, me,
                                likedPostIds.contains(post.getId()),
                                bookmarkedPostIds.contains(post.getId()),
                                repostedPostIds.contains(post.getId()),
                                bookmarkCountMap.getOrDefault(post.getId(), 0));
                    }
                })
                .collect(Collectors.toList());
    }

    private List<CheerPost> filterVisiblePosts(List<CheerPost> posts, UserEntity me) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        Long viewerId = me != null ? me.getId() : null;
        return filterPostsWithAuthor(posts).stream()
                .filter(post -> publicVisibilityVerifier.canAccess(post.getAuthor(), viewerId))
                .toList();
    }

    private List<CheerPost> filterPostsWithAuthor(List<CheerPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        return posts.stream()
                .filter(post -> {
                    if (post.getAuthor() == null) {
                        log.warn("Skipping cheer post with missing author reference: postId={}", post.getId());
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    private PostSummaryRes buildFallbackPostSummary(
            CheerPost post,
            UserEntity me,
            boolean likedByMe,
            boolean bookmarkedByMe,
            boolean repostedByMe,
            int bookmarkCount) {
        boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
        return PostSummaryRes.of(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                post.getContent(),
                resolveDisplayName(post.getAuthor()),
                resolveAuthorHandle(post.getAuthor()),
                resolveAuthorProfileImageUrl(post.getAuthor()),
                resolveAuthorTeamId(post.getAuthor()),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.getLikeCount(),
                bookmarkCount,
                likedByMe,
                safeInt(post.getViews()),
                false,
                bookmarkedByMe,
                isOwner,
                post.getRepostCount(),
                repostedByMe,
                resolvePostTypeName(post),
                Collections.emptyList());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveTeamName(com.example.kbo.entity.TeamEntity team) {
        return team != null ? team.getTeamName() : null;
    }

    private String resolveTeamShortName(com.example.kbo.entity.TeamEntity team) {
        return team != null ? team.getTeamShortName() : null;
    }

    private String resolveTeamColor(com.example.kbo.entity.TeamEntity team) {
        return team != null ? team.getColor() : null;
    }

    private String resolveDisplayName(com.example.auth.entity.UserEntity author) {
        if (author == null) {
            return "사용자";
        }
        if (author.getName() != null && !author.getName().isBlank()) {
            return author.getName();
        }
        if (author.getHandle() != null && !author.getHandle().isBlank()) {
            return author.getHandle();
        }
        return "사용자";
    }

    private String resolveAuthorProfileImageUrl(com.example.auth.entity.UserEntity author) {
        if (author == null) {
            return null;
        }
        String rawValue = author.getProfileImageUrl();
        try {
            String resolved = profileImageService.getProfileImageUrlForCheerFeed(rawValue, author.getProfileFeedImageUrl());
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        } catch (Exception e) {
            log.warn("Cheer feed profile image URL 정규화 실패: userId={}, error={}", author.getId(), e.getMessage());
        }
        if (rawValue != null && !rawValue.isBlank()) {
            if (rawValue.startsWith("http://") || rawValue.startsWith("https://") || rawValue.startsWith("/")) {
                return rawValue;
            }
        }
        return null;
    }

    private String resolveAuthorHandle(com.example.auth.entity.UserEntity author) {
        if (author == null) {
            return null;
        }
        String handle = author.getHandle();
        return handle == null || handle.isBlank() ? null : handle;
    }

    private String resolveAuthorTeamId(com.example.auth.entity.UserEntity author) {
        if (author == null) {
            return null;
        }
        return author.getFavoriteTeamId();
    }

    private String resolvePostTypeName(CheerPost post) {
        if (post == null || post.getPostType() == null) {
            return PostType.NORMAL.name();
        }
        return post.getPostType().name();
    }

    private Map<Long, List<String>> prefetchRepostOriginalImages(List<CheerPost> posts) {
        List<Long> repostOriginalIds = posts.stream()
                .filter(CheerPost::isRepost)
                .map(CheerPost::getRepostOf)
                .filter(Objects::nonNull)
                .map(CheerPost::getId)
                .distinct()
                .toList();
        return safeGetPostImageUrls(repostOriginalIds);
    }

    private java.util.Set<Long> getExcludedUserIds(UserEntity me) {
        if (me == null)
            return java.util.Collections.emptySet();

        java.util.Set<Long> excluded = new java.util.HashSet<>();
        excluded.addAll(blockService.getBlockedIds(me.getId()));
        excluded.addAll(blockService.getBlockerIds(me.getId()));
        return excluded;
    }

    private Map<Long, List<String>> safeGetPostImageUrls(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return imageService.getPostImageUrlsByPostIds(postIds);
        } catch (Exception e) {
            log.warn("Cheer feed image enrichment failed. postCount={}", postIds.size(), e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, Integer> safeGetViewCounts(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return redisPostService.getViewCounts(postIds);
        } catch (Exception e) {
            log.warn("Cheer feed view-count enrichment failed. postCount={}", postIds.size(), e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, Boolean> safeGetHotStatusMap(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return redisPostService.getCachedHotStatuses(postIds);
        } catch (Exception e) {
            log.warn("Cheer feed hot-status enrichment failed. postCount={}", postIds.size(), e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, Integer> safeGetBookmarkCountMap(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return interactionService.getBookmarkCountMap(postIds);
        } catch (Exception e) {
            log.warn("Cheer feed bookmark-count enrichment failed. postCount={}", postIds.size(), e);
            return Collections.emptyMap();
        }
    }

    private Set<Long> safeGetLikedPostIds(UserEntity me, List<Long> postIds) {
        try {
            return interactionService.getLikedPostIds(me.getId(), postIds);
        } catch (Exception e) {
            log.warn("Cheer feed liked-status enrichment failed. userId={}, postCount={}", me.getId(), postIds.size(),
                    e);
            return Collections.emptySet();
        }
    }

    private Set<Long> safeGetBookmarkedPostIds(UserEntity me, List<Long> postIds) {
        try {
            return interactionService.getBookmarkedPostIds(me.getId(), postIds);
        } catch (Exception e) {
            log.warn("Cheer feed bookmark-status enrichment failed. userId={}, postCount={}", me.getId(),
                    postIds.size(), e);
            return Collections.emptySet();
        }
    }

    private Set<Long> safeGetRepostedPostIds(UserEntity me, List<Long> postIds) {
        try {
            return interactionService.getRepostedPostIds(me.getId(), postIds);
        } catch (Exception e) {
            log.warn("Cheer feed repost-status enrichment failed. userId={}, postCount={}", me.getId(), postIds.size(),
                    e);
            return Collections.emptySet();
        }
    }

    private record ScoredHotPost(CheerPost post, double score) {
    }
}
