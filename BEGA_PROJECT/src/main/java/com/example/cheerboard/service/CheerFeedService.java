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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheerFeedService {

    private static final int DEFAULT_FEED_ENRICHMENT_MAX_CONCURRENCY = 32;
    private static final long DEFAULT_FEED_ENRICHMENT_PERMIT_WAIT_TIMEOUT_MS = 50L;
    private static final long DEFAULT_FEED_ENRICHMENT_TASK_TIMEOUT_MS = 800L;
    private static final int DEFAULT_CHANGES_POLL_MAX_SCAN_SIZE = 200;

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
    private final CheerMonitoringMetricsService metricsService;

    private ExecutorService feedEnrichmentExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Semaphore feedEnrichmentBulkhead = new Semaphore(DEFAULT_FEED_ENRICHMENT_MAX_CONCURRENCY);
    private volatile int feedEnrichmentBulkheadPermits = DEFAULT_FEED_ENRICHMENT_MAX_CONCURRENCY;

    @Value("${app.cheer.feed.enrichment.max-concurrency:32}")
    private int feedEnrichmentMaxConcurrency = DEFAULT_FEED_ENRICHMENT_MAX_CONCURRENCY;

    @Value("${app.cheer.feed.enrichment.permit-wait-timeout-ms:50}")
    private long feedEnrichmentPermitWaitTimeoutMs = DEFAULT_FEED_ENRICHMENT_PERMIT_WAIT_TIMEOUT_MS;

    @Value("${app.cheer.feed.enrichment.task-timeout-ms:800}")
    private long feedEnrichmentTaskTimeoutMs = DEFAULT_FEED_ENRICHMENT_TASK_TIMEOUT_MS;

    @Value("${app.cheer.feed.changes.max-scan-size:200}")
    private int changesPollMaxScanSize = DEFAULT_CHANGES_POLL_MAX_SCAN_SIZE;

    @PostConstruct
    void registerFeedEnrichmentBulkheadMetrics() {
        metricsService.registerFeedEnrichmentBulkheadMetrics(
                this,
                CheerFeedService::activeFeedEnrichmentBulkheadPermits,
                CheerFeedService::feedEnrichmentBulkheadLimit);
    }

    @PreDestroy
    private void shutdownExecutor() {
        feedEnrichmentExecutor.shutdownNow();
    }

    /**
     * @DataJpaTest 등 동일 트랜잭션에서 결과를 검증해야 하는 테스트 전용 — 운영 코드에서 호출 금지.
     * Direct executor는 호출 스레드에서 작업을 실행하므로 비동기 타임아웃 동작을 재현하지 않는다.
     */
    public void setFeedEnrichmentExecutorForTest(ExecutorService executor) {
        this.feedEnrichmentExecutor = executor;
    }

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
        long startedAtNanos = System.nanoTime();
        String normalizedTeamId = resolveTeamFilter(teamId);

        try {
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
            Page<PostSummaryRes> response = buildPostSummaryPage(page, me);
            recordFeedRequest("feed", normalizedTeamId, postTypeStr, null, pageable, me, "success", startedAtNanos);
            return response;
        } catch (RuntimeException ex) {
            recordFeedRequest("feed", normalizedTeamId, postTypeStr, null, pageable, me, "failure", startedAtNanos);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> search(String q, String teamId, Pageable pageable, UserEntity me) {
        long startedAtNanos = System.nanoTime();
        String normalizedTeamId = resolveTeamFilter(teamId);
        try {
            Specification<CheerPost> spec = notSimpleRepost()
                    .and(teamMatches(normalizedTeamId))
                    .and(contentContains(q))
                    .and(visibleToViewer(me));
            Page<CheerPost> page = postRepo.findAll(spec, pageable);

            Page<PostSummaryRes> response = buildPostSummaryPage(page, me);
            recordFeedRequest("search", normalizedTeamId, null, null, pageable, me, "success", startedAtNanos);
            return response;
        } catch (RuntimeException ex) {
            recordFeedRequest("search", normalizedTeamId, null, null, pageable, me, "failure", startedAtNanos);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<PostLightweightSummaryRes> listLightweight(String teamId, String postTypeStr, Pageable pageable,
            UserEntity me) {
        long startedAtNanos = System.nanoTime();
        String normalizedTeamId = resolveTeamFilter(teamId);
        try {
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
            List<UserEntity> feedProfileAuthors = feedProfileAuthors(visiblePosts);

            CompletableFuture<Map<Long, List<String>>> imageFuture = postIds.isEmpty()
                    ? CompletableFuture.completedFuture(Collections.emptyMap())
                    : supplyEnrichmentAsync(() -> getPostImageUrls(postIds), Collections.emptyMap());
            CompletableFuture<Map<Long, String>> feedProfileImageFuture = feedProfileAuthors.isEmpty()
                    ? CompletableFuture.completedFuture(Collections.emptyMap())
                    : supplyEnrichmentAsync(() -> resolveFeedProfileImageUrls(feedProfileAuthors), Collections.emptyMap());
            CompletableFuture.allOf(imageFuture, feedProfileImageFuture).join();

            Map<Long, List<String>> imageUrlsByPostId = imageFuture.join();
            Map<Long, String> feedProfileImageUrls = feedProfileImageFuture.join();

            List<PostLightweightSummaryRes> content = visiblePosts.stream().map(post -> {
                try {
                    List<String> imageUrls = imageUrlsByPostId.getOrDefault(post.getId(), Collections.emptyList());
                    return postDtoMapper.toPostLightweightSummaryRes(post, imageUrls, feedProfileImageUrls);
                } catch (Exception e) {
                    log.warn("Cheer lightweight feed enrichment failed for postId={}, fallback to minimal response",
                            post.getId(), e);
                    return buildFallbackPostLightweightSummary(post, feedProfileImageUrls);
                }
            }).toList();

            Page<PostLightweightSummaryRes> response = new PageImpl<>(content, pageable, page.getTotalElements());
            recordFeedRequest(
                    "feed_lightweight",
                    normalizedTeamId,
                    postTypeStr,
                    null,
                    pageable,
                    me,
                    "success",
                    startedAtNanos);
            return response;
        } catch (RuntimeException ex) {
            recordFeedRequest(
                    "feed_lightweight",
                    normalizedTeamId,
                    postTypeStr,
                    null,
                    pageable,
                    me,
                    "failure",
                    startedAtNanos);
            throw ex;
        }
    }

    private com.example.cheerboard.dto.PostLightweightSummaryRes buildFallbackPostLightweightSummary(CheerPost post) {
        return buildFallbackPostLightweightSummary(post, Collections.emptyMap());
    }

    private com.example.cheerboard.dto.PostLightweightSummaryRes buildFallbackPostLightweightSummary(
            CheerPost post,
            Map<Long, String> feedProfileImageUrls) {
        return com.example.cheerboard.dto.PostLightweightSummaryRes.of(
                post.getId(),
                post.getContent(),
                null,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt(),
                resolveDisplayName(post.getAuthor()),
                resolveAuthorProfileImageUrl(post.getAuthor(), feedProfileImageUrls));
    }

    @Transactional(readOnly = true)
    public PostChangesResponse checkPostChanges(Long sinceId, String teamId, UserEntity me) {
        long startedAt = System.nanoTime();
        String normalizedTeamId = null;
        int scannedCount = 0;
        int visibleCount = 0;
        String result = "success";

        try {
            normalizedTeamId = resolveTeamFilter(teamId);
            if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
                if (me == null) {
                    throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
                }
                permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
            }

            long resolvedSinceId = sinceId != null ? sinceId : 0L;
            Pageable scanPageable = PageRequest.of(0, normalizedChangesPollMaxScanSize());
            List<CheerPost> scannedPosts = normalizedTeamId == null
                    ? postRepo.findNewPostsSinceOrderByIdAsc(resolvedSinceId, scanPageable)
                    : postRepo.findNewTeamPostsSinceOrderByIdAsc(resolvedSinceId, normalizedTeamId, scanPageable);
            scannedCount = scannedPosts.size();
            List<CheerPost> visibleNewPosts = filterVisiblePosts(scannedPosts, me);
            int newCount = visibleNewPosts.size();
            visibleCount = newCount;
            Long latestScannedId = scannedPosts.stream()
                    .map(CheerPost::getId)
                    .max(Long::compareTo)
                    .orElse(sinceId);

            return new PostChangesResponse(newCount, latestScannedId);
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metricsService.recordPostChangesPolling(
                    normalizedTeamId != null,
                    scannedCount,
                    visibleCount,
                    System.nanoTime() - startedAt,
                    result);
        }
    }

    private int normalizedChangesPollMaxScanSize() {
        return Math.max(1, changesPollMaxScanSize);
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

    private void recordFeedRequest(
            String endpoint,
            String normalizedTeamId,
            String postType,
            String algorithm,
            Pageable pageable,
            UserEntity me,
            String result,
            long startedAtNanos) {
        metricsService.recordFeedRequest(
                endpoint,
                normalizedTeamId != null && !normalizedTeamId.isBlank(),
                postType,
                algorithm,
                me != null,
                pageable == null ? 0 : pageable.getPageSize(),
                result,
                System.nanoTime() - startedAtNanos);
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
    public Page<PostSummaryRes> listMyPosts(Pageable pageable, UserEntity me) {
        if (me == null) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }

        Specification<CheerPost> spec = authorIdIn(List.of(me.getId()))
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
        long startedAtNanos = System.nanoTime();
        PopularFeedAlgorithm algorithm = PopularFeedAlgorithm.from(algorithmRaw);
        try {
            Page<PostSummaryRes> response = algorithm == PopularFeedAlgorithm.HYBRID
                    ? getHybridHotPosts(pageable, me)
                    : getGlobalHotPosts(pageable, algorithm, me);
            recordFeedRequest("hot", null, null, algorithm.name(), pageable, me, "success", startedAtNanos);
            return response;
        } catch (RuntimeException ex) {
            recordFeedRequest("hot", null, null, algorithm.name(), pageable, me, "failure", startedAtNanos);
            throw ex;
        }
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
                missingPostIdsFromPosts(orderedPosts, visibleOrderedPosts));

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

    /**
     * Hybrid hot feed candidate cap.
     * 개인화(team/follow affinity) 점수가 사용자별로 달라 사전 ZADD로 대체할 수 없는 구조이지만,
     * 후보 풀이 너무 크면 EntityGraph 적용된 1000건 가량을 매 호출에 in-memory 스코어링 + DB EAGER 조인으로 처리하게 된다.
     * 일반 사용 시나리오(상위 100~200건 내 노출)를 가정하고 cap을 절반으로 낮춰 worst-case 부하를 완화한다.
     */
    private static final int HYBRID_HOT_FEED_CANDIDATE_CAP = 499;

    private Page<PostSummaryRes> getHybridHotPosts(Pageable pageable, UserEntity me) {
        int start = (int) pageable.getOffset();
        int end = start + pageable.getPageSize() - 1;
        int candidateEnd = Math.min(HYBRID_HOT_FEED_CANDIDATE_CAP, end + 200);

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
                missingPostIdsFromIds(candidateIds, orderedCandidatePosts));
        List<CheerPost> visibleCandidatePosts = me == null ? filterVisiblePosts(orderedCandidatePosts, null)
                : orderedCandidatePosts;
        totalElements = pruneExplicitHotEntries(
                PopularFeedAlgorithm.TIME_DECAY,
                totalElements,
                missingPostIdsFromPosts(orderedCandidatePosts, visibleCandidatePosts));
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

    private List<Long> missingPostIdsFromPosts(List<CheerPost> requestedPosts, List<CheerPost> resolvedPosts) {
        if (requestedPosts == null || requestedPosts.isEmpty()) {
            return Collections.emptyList();
        }
        return missingPostIdsFromIds(
                requestedPosts.stream().map(CheerPost::getId).toList(),
                resolvedPosts);
    }

    private List<Long> missingPostIdsFromIds(Set<Long> requestedIds, List<CheerPost> resolvedPosts) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return Collections.emptyList();
        }
        return missingPostIdsFromIds(List.copyOf(requestedIds), resolvedPosts);
    }

    private List<Long> missingPostIdsFromIds(List<Long> requestedIds, List<CheerPost> resolvedPosts) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> resolvedIds = resolvedPosts == null ? Collections.emptySet() : resolvedPosts.stream()
                .map(CheerPost::getId)
                .collect(Collectors.toSet());
        return requestedIds.stream()
                .filter(id -> !resolvedIds.contains(id))
                .toList();
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
        List<Long> repostOriginalIds = repostOriginalIds(mappedPosts);
        List<UserEntity> feedProfileAuthors = feedProfileAuthors(mappedPosts);

        // enrichment 병렬 실행. 인스턴스 전체 동시성은 bulkhead로 제한해 Redis/DB fan-out을 완화한다.
        CompletableFuture<Map<Long, List<String>>> imageFuture =
                supplyEnrichmentAsync(() -> getPostImageUrls(postIds), Collections.emptyMap());
        CompletableFuture<Map<Long, List<String>>> repostImageFuture = repostOriginalIds.isEmpty()
                ? CompletableFuture.completedFuture(Collections.emptyMap())
                : supplyEnrichmentAsync(() -> getPostImageUrls(repostOriginalIds), Collections.emptyMap());
        CompletableFuture<Map<Long, Integer>> viewCountFuture =
                supplyEnrichmentAsync(() -> getViewCounts(postIds), Collections.emptyMap());
        CompletableFuture<Map<Long, Integer>> bookmarkCountFuture =
                supplyEnrichmentAsync(() -> getBookmarkCountMap(postIds), Collections.emptyMap());
        CompletableFuture<Set<Long>> likedFuture = me != null
                ? supplyEnrichmentAsync(() -> getLikedPostIds(me, postIds), Collections.emptySet())
                : CompletableFuture.completedFuture(Collections.emptySet());
        CompletableFuture<Set<Long>> bookmarkedFuture = me != null
                ? supplyEnrichmentAsync(() -> getBookmarkedPostIds(me, postIds), Collections.emptySet())
                : CompletableFuture.completedFuture(Collections.emptySet());
        CompletableFuture<Set<Long>> repostedFuture = me != null
                ? supplyEnrichmentAsync(() -> getRepostedPostIds(me, postIds), Collections.emptySet())
                : CompletableFuture.completedFuture(Collections.emptySet());
        CompletableFuture<Map<Long, String>> feedProfileImageFuture = feedProfileAuthors.isEmpty()
                ? CompletableFuture.completedFuture(Collections.emptyMap())
                : supplyEnrichmentAsync(() -> resolveFeedProfileImageUrls(feedProfileAuthors), Collections.emptyMap());

        CompletableFuture.allOf(
                imageFuture, repostImageFuture, viewCountFuture,
                bookmarkCountFuture, likedFuture, bookmarkedFuture, repostedFuture, feedProfileImageFuture
        ).join();

        Map<Long, List<String>> imageUrlsByPostId = imageFuture.join();
        Map<Long, List<String>> repostImageUrls = repostImageFuture.join();
        Map<Long, Integer> viewCountMap = viewCountFuture.join();
        Map<Long, Integer> bookmarkCountMap = bookmarkCountFuture.join();
        Set<Long> likedPostIds = likedFuture.join();
        Set<Long> bookmarkedPostIds = bookmarkedFuture.join();
        Set<Long> repostedPostIds = repostedFuture.join();
        Map<Long, String> feedProfileImageUrls = feedProfileImageFuture.join();

        return mappedPosts.stream()
                .map(post -> {
                    try {
                        boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
                        List<String> imageUrls = imageUrlsByPostId.getOrDefault(post.getId(), Collections.emptyList());
                        return postDtoMapper.toPostSummaryRes(post, likedPostIds.contains(post.getId()),
                                bookmarkedPostIds.contains(post.getId()), isOwner,
                                repostedPostIds.contains(post.getId()),
                                bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                                viewCountMap, Collections.emptyMap(), repostImageUrls, feedProfileImageUrls);
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

    private <T> CompletableFuture<T> supplyEnrichmentAsync(Supplier<T> supplier, T fallback) {
        CompletableFuture<T> taskResult = new CompletableFuture<>();
        Future<?> submittedTask = submitEnrichmentTask(taskResult, supplier);
        CompletableFuture<T> timedResult = taskResult.orTimeout(
                normalizedFeedEnrichmentTaskTimeoutMs(),
                TimeUnit.MILLISECONDS);

        timedResult.whenComplete((value, exception) -> {
            if (submittedTask != null
                    && exception != null
                    && unwrapAsyncException(exception) instanceof TimeoutException) {
                submittedTask.cancel(true);
            }
        });

        return timedResult.handle((value, exception) -> {
            if (exception == null) {
                metricsService.recordFeedEnrichment("success");
                return value;
            }

            Throwable cause = unwrapAsyncException(exception);
            String result = cause instanceof TimeoutException
                    ? "timeout"
                    : cause instanceof FeedEnrichmentBusyException ? "busy" : "failure";
            metricsService.recordFeedEnrichment(result);
            log.debug("Cheer feed enrichment returned fallback: {}", cause.toString());
            if (cause instanceof FeedEnrichmentDegradedException degradedException) {
                return degradedException.fallbackValue();
            }
            return fallback;
        });
    }

    private <T> Future<?> submitEnrichmentTask(CompletableFuture<T> taskResult, Supplier<T> supplier) {
        try {
            return feedEnrichmentExecutor.submit(() -> {
                try {
                    taskResult.complete(runWithEnrichmentPermit(supplier));
                } catch (Throwable exception) {
                    taskResult.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            taskResult.completeExceptionally(new FeedEnrichmentBusyException("Cheer feed enrichment executor is busy"));
            return null;
        }
    }

    private <T> T runWithEnrichmentPermit(Supplier<T> supplier) {
        Semaphore bulkhead = currentFeedEnrichmentBulkhead();
        boolean acquired = false;
        try {
            acquired = bulkhead.tryAcquire(
                    normalizedFeedEnrichmentPermitWaitTimeoutMs(),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new FeedEnrichmentBusyException("Cheer feed enrichment bulkhead is busy");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for cheer feed enrichment permit", e);
        } finally {
            if (acquired) {
                bulkhead.release();
            }
        }
    }

    private Throwable unwrapAsyncException(Throwable exception) {
        Throwable current = exception;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class FeedEnrichmentBusyException extends RuntimeException {
        private FeedEnrichmentBusyException(String message) {
            super(message);
        }
    }

    private static final class FeedEnrichmentDegradedException extends RuntimeException {
        private final Object fallbackValue;

        private FeedEnrichmentDegradedException(Object fallbackValue, Throwable cause) {
            super("Cheer feed enrichment completed with a degraded fallback", cause);
            this.fallbackValue = fallbackValue;
        }

        @SuppressWarnings("unchecked")
        private <T> T fallbackValue() {
            return (T) fallbackValue;
        }
    }

    private Semaphore currentFeedEnrichmentBulkhead() {
        int permits = normalizedFeedEnrichmentMaxConcurrency();
        Semaphore localBulkhead = feedEnrichmentBulkhead;
        if (feedEnrichmentBulkheadPermits == permits) {
            return localBulkhead;
        }

        synchronized (this) {
            if (feedEnrichmentBulkheadPermits != permits) {
                feedEnrichmentBulkhead = new Semaphore(permits);
                feedEnrichmentBulkheadPermits = permits;
            }
            return feedEnrichmentBulkhead;
        }
    }

    private int normalizedFeedEnrichmentMaxConcurrency() {
        return Math.max(1, feedEnrichmentMaxConcurrency);
    }

    private long normalizedFeedEnrichmentPermitWaitTimeoutMs() {
        return Math.max(1L, feedEnrichmentPermitWaitTimeoutMs);
    }

    private long normalizedFeedEnrichmentTaskTimeoutMs() {
        return Math.max(1L, feedEnrichmentTaskTimeoutMs);
    }

    private double activeFeedEnrichmentBulkheadPermits() {
        Semaphore bulkhead = currentFeedEnrichmentBulkhead();
        return Math.max(0, normalizedFeedEnrichmentMaxConcurrency() - bulkhead.availablePermits());
    }

    private double feedEnrichmentBulkheadLimit() {
        return normalizedFeedEnrichmentMaxConcurrency();
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
        try {
            return resolveAuthorProfileImageUrlOrThrow(author);
        } catch (Exception e) {
            log.warn("Cheer feed profile image URL 정규화 실패: userId={}, error={}", author.getId(), e.getMessage());
        }
        return rawProfileImageFallback(author.getProfileImageUrl());
    }

    private String resolveAuthorProfileImageUrlOrThrow(UserEntity author) {
        String rawValue = author.getProfileImageUrl();
        String resolved = profileImageService.getProfileImageUrlForCheerFeed(
                author.getId(),
                rawValue,
                author.getProfileFeedImageUrl());
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return rawProfileImageFallback(rawValue);
    }

    private String rawProfileImageFallback(String rawValue) {
        if (rawValue != null && !rawValue.isBlank()) {
            if (rawValue.startsWith("http://") || rawValue.startsWith("https://") || rawValue.startsWith("/")) {
                return rawValue;
            }
        }
        return null;
    }

    private String resolveAuthorProfileImageUrl(UserEntity author, Map<Long, String> preloadedUrls) {
        if (author == null) {
            return null;
        }
        if (preloadedUrls != null && preloadedUrls.containsKey(author.getId())) {
            return preloadedUrls.get(author.getId());
        }
        return resolveAuthorProfileImageUrl(author);
    }

    private List<UserEntity> feedProfileAuthors(List<CheerPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.LinkedHashMap<Long, UserEntity> authorsById = new java.util.LinkedHashMap<>();
        for (CheerPost post : posts) {
            if (post == null) {
                continue;
            }
            addAuthorById(authorsById, post.getAuthor());
            if (post.isRepost() && post.getRepostOf() != null) {
                addAuthorById(authorsById, post.getRepostOf().getAuthor());
            }
        }
        return List.copyOf(authorsById.values());
    }

    private void addAuthorById(Map<Long, UserEntity> authorsById, UserEntity author) {
        if (author == null || author.getId() == null) {
            return;
        }
        authorsById.putIfAbsent(author.getId(), author);
    }

    private Map<Long, String> resolveFeedProfileImageUrls(List<UserEntity> authors) {
        if (authors == null || authors.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> urlsByAuthorId = new java.util.HashMap<>();
        Throwable firstFailure = null;
        for (UserEntity author : authors) {
            if (author == null || author.getId() == null) {
                continue;
            }
            try {
                urlsByAuthorId.put(author.getId(), resolveAuthorProfileImageUrlOrThrow(author));
            } catch (Exception exception) {
                log.warn(
                        "Cheer feed profile image URL 정규화 실패: userId={}, error={}",
                        author.getId(),
                        exception.getMessage());
                urlsByAuthorId.put(author.getId(), rawProfileImageFallback(author.getProfileImageUrl()));
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }
        if (firstFailure != null) {
            throw new FeedEnrichmentDegradedException(urlsByAuthorId, firstFailure);
        }
        return urlsByAuthorId;
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

    private List<Long> repostOriginalIds(List<CheerPost> posts) {
        return posts.stream()
                .filter(CheerPost::isRepost)
                .map(CheerPost::getRepostOf)
                .filter(Objects::nonNull)
                .map(CheerPost::getId)
                .distinct()
                .toList();
    }

    private java.util.Set<Long> getExcludedUserIds(UserEntity me) {
        if (me == null)
            return java.util.Collections.emptySet();

        java.util.Set<Long> excluded = new java.util.HashSet<>();
        excluded.addAll(blockService.getBlockedIds(me.getId()));
        excluded.addAll(blockService.getBlockerIds(me.getId()));
        return excluded;
    }

    private Map<Long, List<String>> getPostImageUrls(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return imageService.getPostImageUrlsByPostIds(postIds);
    }

    private Map<Long, Integer> getViewCounts(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return redisPostService.getViewCounts(postIds);
    }

    private Map<Long, Integer> safeGetViewCounts(List<Long> postIds) {
        try {
            return getViewCounts(postIds);
        } catch (Exception e) {
            log.warn("Cheer feed view-count enrichment failed. postCount={}", postIds.size(), e);
            return Collections.emptyMap();
        }
    }

    private Map<Long, Integer> getBookmarkCountMap(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return interactionService.getBookmarkCountMap(postIds);
    }

    private Set<Long> getLikedPostIds(UserEntity me, List<Long> postIds) {
        return interactionService.getLikedPostIds(me.getId(), postIds);
    }

    private Set<Long> getBookmarkedPostIds(UserEntity me, List<Long> postIds) {
        return interactionService.getBookmarkedPostIds(me.getId(), postIds);
    }

    private Set<Long> getRepostedPostIds(UserEntity me, List<Long> postIds) {
        return interactionService.getRepostedPostIds(me.getId(), postIds);
    }

    private record ScoredHotPost(CheerPost post, double score) {
    }
}
