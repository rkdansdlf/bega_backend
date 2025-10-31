package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.demo.entity.UserEntity;
import org.springframework.stereotype.Component;

/**
 * CheerPost 엔티티를 DTO로 변환하는 매퍼 클래스
 */
@Component
public class PostDtoMapper {
    
    private final HotPostChecker hotPostChecker;
    
    public PostDtoMapper(HotPostChecker hotPostChecker) {
        this.hotPostChecker = hotPostChecker;
    }
    
    /**
     * CheerPost를 PostSummaryRes로 변환
     */
    public PostSummaryRes toPostSummaryRes(CheerPost post) {
        return new PostSummaryRes(
            post.getId(),
            post.getTeamId(),
            post.getTitle(),
            resolveDisplayName(post.getAuthor()),
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
        return new PostDetailRes(
            post.getId(),
            post.getTeamId(),
            post.getTitle(),
            post.getContent(),
            resolveDisplayName(post.getAuthor()),
            post.getAuthor().getEmail(),
            post.getCreatedAt(),
            post.getCommentCount(),
            post.getLikeCount(),
            liked,
            isOwner,
            post.getImageUrls(),
            post.getViews(),
            post.getPostType().name()
        );
    }
    
    /**
     * 새로 생성된 게시글을 PostDetailRes로 변환 (좋아요/소유권 기본값 설정)
     */
    public PostDetailRes toNewPostDetailRes(CheerPost post, UserEntity author) {
        return new PostDetailRes(
            post.getId(),
            post.getTeamId(),
            post.getTitle(),
            post.getContent(),
            resolveDisplayName(author),
            author.getEmail(),
            post.getCreatedAt(),
            0, // 새 게시글이므로 댓글 수 0
            0, // 새 게시글이므로 좋아요 수 0
            false, // 새 게시글이므로 좋아요 안함
            true, // 작성자이므로 소유권 있음
            post.getImageUrls(),
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
}
