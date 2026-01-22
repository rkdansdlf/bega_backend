package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.TeamEntity;
import com.example.auth.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * CheerPost 엔티티를 DTO로 변환하는 매퍼 클래스
 */
@Slf4j
@Component
public class PostDtoMapper {

    private final HotPostChecker hotPostChecker;
    private final ImageService imageService;
    private final RedisPostService redisPostService;

    public PostDtoMapper(HotPostChecker hotPostChecker, @Lazy ImageService imageService,
            RedisPostService redisPostService) {
        this.hotPostChecker = hotPostChecker;
        this.imageService = imageService;
        this.redisPostService = redisPostService;
    }

    /**
     * CheerPost를 PostSummaryRes로 변환
     */
    public PostSummaryRes toPostSummaryRes(CheerPost post, boolean liked, boolean isBookmarked, boolean isOwner) {
        List<String> imageUrls = Collections.emptyList();
        try {
            imageUrls = imageService.getPostImageUrls(post.getId());
        } catch (Exception e) {
            log.warn("이미지 URL 조회 실패: postId={}, error={}", post.getId(), e.getMessage());
        }

        return toPostSummaryRes(post, liked, isBookmarked, isOwner, imageUrls);
    }

    /**
     * CheerPost를 PostSummaryRes로 변환 (이미지 URL 미리 로딩된 경우)
     */
    public PostSummaryRes toPostSummaryRes(CheerPost post, boolean liked, boolean isBookmarked, boolean isOwner,
            List<String> imageUrls) {
        List<String> resolvedUrls = imageUrls != null ? imageUrls : Collections.emptyList();

        // Redis와 DB 조회수 합산
        Integer redisViews = redisPostService.getViewCount(post.getId());
        int combinedViews = post.getViews() + (redisViews != null ? redisViews : 0);

        // HOT 게시글 상태 캐싱 활용
        Boolean cachedHot = redisPostService.getCachedHotStatus(post.getId());
        boolean isHot;
        if (cachedHot != null) {
            isHot = cachedHot;
        } else {
            // 캐시 없으면 계산 후 캐싱 (계산 시 combinedViews 사용)
            isHot = hotPostChecker.isHotPost(post, combinedViews);
            redisPostService.cacheHotStatus(post.getId(), isHot);
        }

        return new PostSummaryRes(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                post.getTitle(),
                post.getContent(),
                resolveDisplayName(post.getAuthor()),
                post.getAuthor().getId(),
                post.getAuthor().getHandle(),
                post.getAuthor().getProfileImageUrl(),
                post.getAuthor().getFavoriteTeamId(),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.getLikeCount(),
                liked,
                combinedViews, // 합산된 조회수
                isHot,
                isBookmarked,
                isOwner,
                post.getRepostCount(),
                post.getPostType().name(),
                resolvedUrls);
    }

    /**
     * CheerPost를 PostDetailRes로 변환
     */
    public PostDetailRes toPostDetailRes(CheerPost post, boolean liked, boolean isBookmarked, boolean isOwner) {
        List<String> imageUrls = Collections.emptyList();
        try {
            imageUrls = imageService.getPostImageUrls(post.getId());
        } catch (Exception e) {
            log.warn("이미지 URL 조회 실패: postId={}, error={}", post.getId(), e.getMessage());
        }

        // Redis와 DB 조회수 합산
        Integer redisViews = redisPostService.getViewCount(post.getId());
        int combinedViews = post.getViews() + (redisViews != null ? redisViews : 0);

        return new PostDetailRes(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                post.getTitle(),
                post.getContent(),
                resolveDisplayName(post.getAuthor()),
                post.getAuthor().getId(),
                post.getAuthor().getHandle(),
                post.getAuthor().getEmail(),
                post.getAuthor().getProfileImageUrl(),
                post.getCreatedAt(),
                post.getCommentCount(),
                post.getLikeCount(),
                liked,
                isBookmarked,
                isOwner,
                imageUrls,
                combinedViews, // 합산된 조회수
                post.getRepostCount(),
                post.getPostType().name());
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

        return new PostDetailRes(
                post.getId(),
                post.getTeamId(),
                resolveTeamName(post.getTeam()),
                resolveTeamShortName(post.getTeam()),
                resolveTeamColor(post.getTeam()),
                post.getTitle(),
                post.getContent(),
                resolveDisplayName(author),
                author.getId(),
                author.getHandle(),
                author.getEmail(),
                author.getProfileImageUrl(),
                post.getCreatedAt(),
                0, // 새 게시글이므로 댓글 수 0
                0, // 새 게시글이므로 좋아요 수 0
                false, // 새 게시글이므로 좋아요 안함
                false, // 새 게시글이므로 북마크 안함
                true, // 작성자이므로 소유권 있음
                imageUrls,
                0, // 새 게시글이므로 조회수 0
                0, // 새 게시글이므로 리포스트 수 0
                post.getPostType().name());
    }

    private String resolveDisplayName(UserEntity author) {
        if (author.getName() != null && !author.getName().isBlank()) {
            return author.getName();
        }
        return author.getEmail();
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
}
