package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.EmbeddedPostDto;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.SourceInfoRes;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.TeamEntity;
import com.example.auth.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CheerPost 엔티티를 DTO로 변환하는 매퍼 클래스
 */
@Slf4j
@Component
public class PostDtoMapper {

    private final HotPostChecker hotPostChecker;
    private final ImageService imageService;
    private final RedisPostService redisPostService;
    private final com.example.profile.storage.service.ProfileImageService profileImageService;

    public PostDtoMapper(HotPostChecker hotPostChecker, @Lazy ImageService imageService,
            RedisPostService redisPostService,
            com.example.profile.storage.service.ProfileImageService profileImageService) {
        this.hotPostChecker = hotPostChecker;
        this.imageService = imageService;
        this.redisPostService = redisPostService;
        this.profileImageService = profileImageService;
    }

    /**
     * CheerPost를 PostSummaryRes로 변환
     */
    public PostSummaryRes toPostSummaryRes(CheerPost post, boolean liked, boolean isBookmarked, boolean isOwner,
            boolean repostedByMe, int bookmarkCount) {
        List<String> imageUrls = Collections.emptyList();
        try {
            imageUrls = imageService.getPostImageUrls(post.getId());
        } catch (Exception e) {
            log.warn("이미지 URL 조회 실패: postId={}, error={}", post.getId(), e.getMessage());
        }

        return toPostSummaryRes(post, liked, isBookmarked, isOwner, repostedByMe, bookmarkCount, imageUrls);
    }

    /**
     * CheerPost를 PostSummaryRes로 변환 (이미지 URL 미리 로딩된 경우)
     */
    public PostSummaryRes toPostSummaryRes(CheerPost post, boolean liked, boolean isBookmarked, boolean isOwner,
            boolean repostedByMe, int bookmarkCount, List<String> imageUrls) {
        List<String> resolvedUrls = imageUrls != null ? imageUrls : Collections.emptyList();

        // Redis와 DB 조회수 합산
        Integer redisViews = redisPostService.getViewCount(post.getId());
        int combinedViews = post.getViews() + (redisViews != null ? redisViews : 0);

        Boolean cachedHot = redisPostService.getCachedHotStatus(post.getId());
        boolean isHot = resolveHotStatus(post, combinedViews, cachedHot);

        // 리포스트 관련 정보 처리
        Long repostOfId = null;
        String repostType = null;
        EmbeddedPostDto originalPost = null;
        boolean originalDeleted = false;

        if (post.isRepost()) {
            repostType = post.getRepostType().name();
            CheerPost original = post.getRepostOf();

            if (original != null) {
                repostOfId = original.getId();
                originalPost = toEmbeddedPostDto(original);
                originalDeleted = false;
            } else {
                // 원본이 삭제된 경우 (repostOf가 null로 설정됨 - ON DELETE SET NULL)
                originalDeleted = true;
            }
        }

        return new PostSummaryRes(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                post.getContent(),
                resolveDisplayName(post.getAuthor()),
                post.getAuthor().getHandle(),
                resolveAuthorProfileImageUrl(post.getAuthor()),
                post.getAuthor().getFavoriteTeamId(),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.getLikeCount(),
                bookmarkCount,
                liked,
                combinedViews,
                isHot,
                isBookmarked,
                isOwner,
                post.getRepostCount(),
                repostedByMe,
                post.getPostType().name(),
                resolvedUrls,
                repostOfId,
                repostType,
                originalPost,
                originalDeleted,
                resolveShareMode(post),
                toSourceInfo(post));
    }

    /**
     * CheerPost를 PostDetailRes로 변환
     */
    public PostDetailRes toPostDetailRes(CheerPost post, boolean liked, boolean isBookmarked, boolean isOwner,
            boolean repostedByMe, int bookmarkCount) {
        List<String> imageUrls = Collections.emptyList();
        try {
            imageUrls = imageService.getPostImageUrls(post.getId());
        } catch (Exception e) {
            log.warn("이미지 URL 조회 실패: postId={}, error={}", post.getId(), e.getMessage());
        }

        // Redis와 DB 조회수 합산
        Integer redisViews = redisPostService.getViewCount(post.getId());
        int combinedViews = post.getViews() + (redisViews != null ? redisViews : 0);

        // 리포스트 관련 정보 처리
        Long repostOfId = null;
        String repostType = null;
        EmbeddedPostDto originalPost = null;
        boolean originalDeleted = false;

        if (post.isRepost()) {
            repostType = post.getRepostType().name();
            CheerPost original = post.getRepostOf();

            if (original != null) {
                repostOfId = original.getId();
                originalPost = toEmbeddedPostDto(original);
                originalDeleted = false;
            } else {
                // 원본이 삭제된 경우
                originalDeleted = true;
            }
        }

        return new PostDetailRes(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                // title removed
                post.getContent(),
                resolveDisplayName(post.getAuthor()),
                post.getAuthor().getHandle(),
                resolveAuthorProfileImageUrl(post.getAuthor()),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.getLikeCount(),
                bookmarkCount,
                liked,
                isBookmarked,
                isOwner,
                imageUrls,
                combinedViews, // 합산된 조회수
                post.getRepostCount(),
                repostedByMe,
                post.getPostType().name(),
                // 리포스트 관련 필드
                repostOfId,
                repostType,
                originalPost,
                originalDeleted,
                resolveShareMode(post),
                toSourceInfo(post));
    }

    /**
     * 새로 생성된 게시글을 PostDetailRes로 변환 (좋아요/소유권 기본값 설정)
     */
    public PostDetailRes toNewPostDetailRes(CheerPost post, UserEntity author) {
        List<String> imageUrls = Collections.emptyList();
        try {
            imageUrls = imageService.getPostImageUrls(post.getId());
        } catch (Exception e) {
            log.warn("이미지 URL 조회 실패: postId={}, error={}", post.getId(), e.getMessage());
        }

        // 리포스트 관련 정보 처리 (새 게시글이 리포스트인 경우)
        Long repostOfId = null;
        String repostType = null;
        EmbeddedPostDto originalPost = null;
        boolean originalDeleted = false;

        if (post.isRepost() && post.getRepostOf() != null) {
            repostOfId = post.getRepostOf().getId();
            repostType = post.getRepostType().name();
            originalPost = toEmbeddedPostDto(post.getRepostOf());
        }

        return new PostDetailRes(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                // title removed
                post.getContent(),
                resolveDisplayName(author),
                author.getHandle(),
                resolveAuthorProfileImageUrl(author),
                post.getCreatedAt(),
                0, // 새 게시글이므로 댓글 수 0
                0, // 새 게시글이므로 좋아요 수 0
                0, // 새 게시글이므로 북마크 수 0
                false, // 새 게시글이므로 좋아요 안함
                false, // 새 게시글이므로 북마크 안함
                true, // 작성자이므로 소유권 있음
                imageUrls,
                0, // 새 게시글이므로 조회수 0
                0, // 새 게시글이므로 리포스트 수 0
                false, // 새 게시글이므로 리포스트 안함
                post.getPostType().name(),
                // 리포스트 관련 필드
                repostOfId,
                repostType,
                originalPost,
                originalDeleted,
                resolveShareMode(post),
                toSourceInfo(post));
    }

    /**
     * CheerPost를 PostSummaryRes로 변환 (모든 데이터 프리페치 버전)
     * - Redis 조회수/HOT 상태, 리포스트 원본 이미지 모두 미리 로딩된 경우
     */
    public PostSummaryRes toPostSummaryRes(CheerPost post, boolean liked, boolean isBookmarked, boolean isOwner,
            boolean repostedByMe, int bookmarkCount, List<String> imageUrls,
            Map<Long, Integer> viewCountMap, Map<Long, Boolean> hotStatusMap,
            Map<Long, List<String>> repostOriginalImageUrls) {
        List<String> resolvedUrls = imageUrls != null ? imageUrls : Collections.emptyList();

        // 프리페치된 Redis 조회수 사용
        Integer redisViews = viewCountMap.getOrDefault(post.getId(), null);
        int combinedViews = post.getViews() + (redisViews != null ? redisViews : 0);

        Boolean cachedHot = hotStatusMap.get(post.getId());
        boolean isHot = resolveHotStatus(post, combinedViews, cachedHot);

        // 리포스트 관련 정보 처리
        Long repostOfId = null;
        String repostType = null;
        EmbeddedPostDto originalPost = null;
        boolean originalDeleted = false;

        if (post.isRepost()) {
            repostType = post.getRepostType().name();
            CheerPost original = post.getRepostOf();

            if (original != null) {
                repostOfId = original.getId();
                originalPost = toEmbeddedPostDto(original, repostOriginalImageUrls);
                originalDeleted = false;
            } else {
                originalDeleted = true;
            }
        }

        return new PostSummaryRes(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                post.getContent(),
                resolveDisplayName(post.getAuthor()),
                post.getAuthor().getHandle(),
                resolveAuthorProfileImageUrl(post.getAuthor()),
                post.getAuthor().getFavoriteTeamId(),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.getLikeCount(),
                bookmarkCount,
                liked,
                combinedViews,
                isHot,
                isBookmarked,
                isOwner,
                post.getRepostCount(),
                repostedByMe,
                post.getPostType().name(),
                resolvedUrls,
                repostOfId,
                repostType,
                originalPost,
                originalDeleted,
                resolveShareMode(post),
                toSourceInfo(post));
    }

    /**
     * 원본 게시글을 EmbeddedPostDto로 변환 (리포스트 표시용)
     */
    private EmbeddedPostDto toEmbeddedPostDto(CheerPost original) {
        if (original == null) {
            return null;
        }

        List<String> originalImageUrls = Collections.emptyList();
        try {
            originalImageUrls = imageService.getPostImageUrls(original.getId());
        } catch (Exception e) {
            log.warn("원본 게시글 이미지 URL 조회 실패: postId={}, error={}", original.getId(), e.getMessage());
        }

        return EmbeddedPostDto.of(
                original.getId(),
                original.getTeamId(),
                resolveTeamColor(original.getTeam()),
                original.getContent(),
                resolveDisplayName(original.getAuthor()),
                original.getAuthor().getHandle(),
                resolveAuthorProfileImageUrl(original.getAuthor()),
                original.getCreatedAt(),
                originalImageUrls,
                original.getLikeCount(),
                original.getCommentCount(),
                original.getRepostCount());
    }

    /**
     * 원본 게시글을 EmbeddedPostDto로 변환 (프리페치된 이미지 URL 사용)
     */
    private EmbeddedPostDto toEmbeddedPostDto(CheerPost original, Map<Long, List<String>> preloadedImageUrls) {
        if (original == null) {
            return null;
        }

        List<String> originalImageUrls = preloadedImageUrls != null
                ? preloadedImageUrls.getOrDefault(original.getId(), Collections.emptyList())
                : Collections.emptyList();

        return EmbeddedPostDto.of(
                original.getId(),
                original.getTeamId(),
                resolveTeamColor(original.getTeam()),
                original.getContent(),
                resolveDisplayName(original.getAuthor()),
                original.getAuthor().getHandle(),
                resolveAuthorProfileImageUrl(original.getAuthor()),
                original.getCreatedAt(),
                originalImageUrls,
                original.getLikeCount(),
                original.getCommentCount(),
                original.getRepostCount());
    }

    private boolean resolveHotStatus(CheerPost post, int combinedViews, Boolean cachedHot) {
        boolean computedHot = hotPostChecker.isHotPost(post, combinedViews);
        if (cachedHot == null || cachedHot.booleanValue() != computedHot) {
            redisPostService.cacheHotStatus(post.getId(), computedHot);
        }
        return computedHot;
    }

    private String resolveDisplayName(UserEntity author) {
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

    private String resolveTeamName(TeamEntity team) {
        return team != null ? team.getTeamName() : null;
    }

    private String resolveTeamShortName(TeamEntity team) {
        return team != null ? team.getTeamShortName() : null;
    }

    private String resolveTeamColor(TeamEntity team) {
        return team != null ? team.getColor() : null;
    }

    private String resolveShareMode(CheerPost post) {
        return post.getShareMode() != null ? post.getShareMode().name() : null;
    }

    private SourceInfoRes toSourceInfo(CheerPost post) {
        boolean hasSourceInfo = post.getSourceUrl() != null
                || post.getSourceTitle() != null
                || post.getSourceAuthor() != null
                || post.getSourceLicense() != null
                || post.getSourceLicenseUrl() != null
                || post.getSourceChangedNote() != null
                || post.getSourceSnapshotType() != null;
        if (!hasSourceInfo) {
            return null;
        }

        return new SourceInfoRes(
                post.getSourceTitle(),
                post.getSourceAuthor(),
                post.getSourceUrl(),
                post.getSourceLicense(),
                post.getSourceLicenseUrl(),
                post.getSourceChangedNote(),
                post.getSourceSnapshotType());
    }

    /**
     * CheerPost를 PostLightweightSummaryRes로 변환 (최소 데이터만 포함)
     * - 리스트 조회 시 페이로드 최소화
     * - 폴링 엔드포인트에서 사용
     */
    public PostLightweightSummaryRes toPostLightweightSummaryRes(CheerPost post, List<String> imageUrls) {
        String firstImageUrl = (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null;

        return PostLightweightSummaryRes.of(
                post.getId(),
                post.getContent(),
                firstImageUrl,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt(),
                resolveDisplayName(post.getAuthor()),
                resolveAuthorProfileImageUrl(post.getAuthor()));
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
}
