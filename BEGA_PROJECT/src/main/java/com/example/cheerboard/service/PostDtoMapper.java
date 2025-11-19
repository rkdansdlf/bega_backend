package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.Team;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.storage.service.ImageService;
import com.example.demo.entity.UserEntity;
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

    public PostDtoMapper(HotPostChecker hotPostChecker, @Lazy ImageService imageService) {
        this.hotPostChecker = hotPostChecker;
        this.imageService = imageService;
    }
    
    /**
     * CheerPost를 PostSummaryRes로 변환
     */
    public PostSummaryRes toPostSummaryRes(CheerPost post) {
        return new PostSummaryRes(
            post.getId(),
            post.getTeamId(),
            resolveTeamName(post.getTeam()),
            resolveTeamShortName(post.getTeam()),
            resolveTeamColor(post.getTeam()),
            post.getTitle(),
            resolveDisplayName(post.getAuthor()),
            post.getAuthor().getProfileImageUrl(),
            post.getCreatedAt(),
            post.getCommentCount(),
            post.getLikeCount(),
            post.getViews(),
            hotPostChecker.isHotPost(post),
            post.getPostType().name()
        );
    }
    
    /**
     * CheerPost를 PostDetailRes로 변환
     */
    public PostDetailRes toPostDetailRes(CheerPost post, boolean liked, boolean isOwner) {
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
            resolveDisplayName(post.getAuthor()),
            post.getAuthor().getEmail(),
            post.getAuthor().getProfileImageUrl(),
            post.getCreatedAt(),
            post.getCommentCount(),
            post.getLikeCount(),
            liked,
            isOwner,
            imageUrls,
            post.getViews(),
            post.getPostType().name()
        );
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
            author.getEmail(),
            author.getProfileImageUrl(),
            post.getCreatedAt(),
            0, // 새 게시글이므로 댓글 수 0
            0, // 새 게시글이므로 좋아요 수 0
            false, // 새 게시글이므로 좋아요 안함
            true, // 작성자이므로 소유권 있음
            imageUrls,
            0, // 새 게시글이므로 조회수 0
            post.getPostType().name()
        );
    }

    private String resolveDisplayName(UserEntity author) {
        if (author.getName() != null && !author.getName().isBlank()) {
            return author.getName();
        }
        return author.getEmail();
    }

    private String resolveTeamName(Team team) {
        return team != null ? team.getName() : null;
    }

    private String resolveTeamShortName(Team team) {
        return team != null ? team.getShortName() : null;
    }

    private String resolveTeamColor(Team team) {
        return team != null ? team.getColor() : null;
    }
}
