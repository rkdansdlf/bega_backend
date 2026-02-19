package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostChangesResponse;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.kbo.util.TeamCodeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
    private final PermissionValidator permissionValidator;
    private final PostDtoMapper postDtoMapper;

    // We need to resolve Normalized Team ID, so call utility directly or via helper

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> list(String teamId, String postTypeStr, Pageable pageable, UserEntity me) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);

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

        java.util.Set<Long> excludedIds = getExcludedUserIds(me);
        log.debug("List - excludedIds size: {}", excludedIds.size());

        Page<CheerPost> page;
        boolean hasSort = pageable.getSort().isSorted();

        if (hasSort && pageable.getSort().stream().anyMatch(order -> !order.getProperty().equals("createdAt"))) {
            page = postRepo.findByTeamIdAndPostType(normalizedTeamId, postType, excludedIds, pageable);
        } else {
            java.time.Instant cutoffDate = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS);
            page = postRepo.findAllOrderByPostTypeAndCreatedAt(normalizedTeamId, postType, cutoffDate, excludedIds,
                    pageable);
        }

        return buildPostSummaryPage(page, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> search(String q, String teamId, Pageable pageable, UserEntity me) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);
        java.util.Set<Long> excludedIds = getExcludedUserIds(me);
        Page<CheerPost> page = postRepo.search(q, normalizedTeamId, excludedIds, pageable);

        return buildPostSummaryPage(page, me);
    }

    @Transactional(readOnly = true)
    public Page<PostLightweightSummaryRes> listLightweight(String teamId, String postTypeStr, Pageable pageable,
            UserEntity me) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);
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

        java.util.Set<Long> excludedIds = getExcludedUserIds(me);
        Page<CheerPost> page;
        boolean hasSort = pageable.getSort().isSorted();

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

        return page.map(post -> {
            List<String> imageUrls = imageUrlsByPostId.getOrDefault(post.getId(), Collections.emptyList());
            return postDtoMapper.toPostLightweightSummaryRes(post, imageUrls);
        });
    }

    @Transactional(readOnly = true)
    public PostChangesResponse checkPostChanges(Long sinceId, String teamId, UserEntity me) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);
        if (normalizedTeamId != null && !normalizedTeamId.isBlank()) {
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, normalizedTeamId, "게시글 조회");
        }

        int newCount = postRepo.countNewPostsSince(sinceId != null ? sinceId : 0L, normalizedTeamId);
        Long latestId = postRepo.findLatestPostId(normalizedTeamId);

        return new PostChangesResponse(newCount, latestId);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listFollowingPosts(Pageable pageable, UserEntity me) {
        if (me == null) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }

        List<Long> followingIds = followService.getFollowingIds(me.getId());
        if (followingIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<Long> blockedIds = blockService.getBlockedIds(me.getId());

        Page<CheerPost> page;
        if (blockedIds.isEmpty()) {
            page = postRepo.findByAuthorIdIn(followingIds, pageable);
        } else {
            page = postRepo.findByAuthorIdInAndAuthorIdNotIn(followingIds, blockedIds, pageable);
        }

        return buildPostSummaryPage(page, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listByUserHandle(String handle, Pageable pageable, UserEntity me) {
        Page<CheerPost> page = postRepo.findByAuthor_HandleOrderByCreatedAtDesc(handle, pageable);
        return buildPostSummaryPage(page, me);
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

        Set<Long> hotPostIds = redisPostService.getHotPostIds(start, end, algorithm);
        if (hotPostIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, redisPostService.getHotListSize(algorithm));
        }

        List<CheerPost> posts = postRepo.findAllByIdWithGraph(hotPostIds);

        // 차단 필터링
        java.util.Set<Long> excludedIds = getExcludedUserIds(me);

        // Redis 순서 유지 정렬
        Map<Long, CheerPost> postMap = posts.stream()
                .collect(Collectors.toMap(CheerPost::getId, Function.identity()));
        List<CheerPost> sortedPosts = hotPostIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .filter(post -> !excludedIds.contains(post.getAuthor().getId()))
                .toList();

        List<PostSummaryRes> content = toHotPostSummary(sortedPosts, me);
        return new PageImpl<>(content, pageable, redisPostService.getHotListSize(algorithm));
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
        List<CheerPost> candidatePosts = candidateIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .filter(post -> !excludedIds.contains(post.getAuthor().getId()))
                .toList();

        if (candidatePosts.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, totalElements);
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
        return new PageImpl<>(content, pageable, totalElements);
    }

    private List<PostSummaryRes> toHotPostSummary(List<CheerPost> posts, UserEntity me) {
        if (posts.isEmpty())
            return Collections.emptyList();

        // Convert List<CheerPost> to Page<CheerPost> just for reusing
        // buildPostSummaryPage logic?
        // No, buildPostSummaryPage does pagination stuff or just maps content.
        // It returns a Page.
        // Let's refactor: Create a helper `buildPostSummaries` that returns List, and
        // buildPostSummaryPage calls it.
        return buildPostSummaries(posts, me);
    }

    // --- Helpers ---

    private Page<PostSummaryRes> buildPostSummaryPage(Page<CheerPost> page, UserEntity me) {
        if (!page.hasContent()) {
            return new PageImpl<>(Collections.emptyList(), page.getPageable(), page.getTotalElements());
        }
        List<PostSummaryRes> content = buildPostSummaries(page.getContent(), me);
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private List<PostSummaryRes> buildPostSummaries(List<CheerPost> posts, UserEntity me) {
        List<Long> postIds = posts.stream().map(CheerPost::getId).toList();

        Map<Long, List<String>> imageUrlsByPostId = postIds.isEmpty()
                ? Collections.emptyMap()
                : imageService.getPostImageUrlsByPostIds(postIds);

        Map<Long, List<String>> repostImageUrls = prefetchRepostOriginalImages(posts);
        Map<Long, Integer> viewCountMap = redisPostService.getViewCounts(postIds);
        Map<Long, Boolean> hotStatusMap = redisPostService.getCachedHotStatuses(postIds);
        // Use InteractionService for counts and statuses
        Map<Long, Integer> bookmarkCountMap = interactionService.getBookmarkCountMap(postIds);

        Set<Long> likedPostIds = (me != null) ? interactionService.getLikedPostIds(me.getId(), postIds)
                : Collections.emptySet();
        Set<Long> bookmarkedPostIds = (me != null) ? interactionService.getBookmarkedPostIds(me.getId(), postIds)
                : Collections.emptySet();
        Set<Long> repostedPostIds = (me != null) ? interactionService.getRepostedPostIds(me.getId(), postIds)
                : Collections.emptySet();

        return posts.stream()
                .map(post -> {
                    boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
                    List<String> imageUrls = imageUrlsByPostId.getOrDefault(post.getId(), Collections.emptyList());
                    return postDtoMapper.toPostSummaryRes(post, likedPostIds.contains(post.getId()),
                            bookmarkedPostIds.contains(post.getId()), isOwner, repostedPostIds.contains(post.getId()),
                            bookmarkCountMap.getOrDefault(post.getId(), 0), imageUrls,
                            viewCountMap, hotStatusMap, repostImageUrls);
                })
                .collect(Collectors.toList());
    }

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

    private java.util.Set<Long> getExcludedUserIds(UserEntity me) {
        if (me == null)
            return java.util.Collections.emptySet();

        java.util.Set<Long> excluded = new java.util.HashSet<>();
        excluded.addAll(blockService.getBlockedIds(me.getId()));
        excluded.addAll(blockService.getBlockerIds(me.getId()));
        return excluded;
    }

    private record ScoredHotPost(CheerPost post, double score) {
    }
}
