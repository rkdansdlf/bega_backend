package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import org.springframework.stereotype.Component;

import static com.example.cheerboard.service.CheerServiceConstants.*;

/**
 * HOT 게시글 판정 로직을 담당하는 클래스
 */
@Component
public class HotPostChecker {
    
    /**
     * 게시글이 HOT 게시글인지 판정
     * 
     * 판정 기준:
     * 1. 좋아요 10개 이상
     * 2. 조회수 20회 이상  
     * 3. 좋아요 5개 이상 + 댓글 3개 이상
     * 
     * @param post 판정할 게시글
     * @return HOT 게시글 여부
     */
    public boolean isHotPost(CheerPost post) {
        return hasHighLikes(post) ||
               hasHighViews(post) ||
               hasModerateEngagement(post);
    }
    
    /**
     * 높은 좋아요 수를 가지는지 확인
     */
    private boolean hasHighLikes(CheerPost post) {
        return post.getLikeCount() >= HOT_LIKE_THRESHOLD;
    }
    
    /**
     * 높은 조회수를 가지는지 확인
     */
    private boolean hasHighViews(CheerPost post) {
        return post.getViews() >= HOT_VIEW_THRESHOLD;
    }
    
    /**
     * 적당한 참여도(좋아요 + 댓글)를 가지는지 확인
     */
    private boolean hasModerateEngagement(CheerPost post) {
        return post.getLikeCount() >= HOT_LIKE_WITH_COMMENT_THRESHOLD &&
               post.getCommentCount() >= HOT_COMMENT_THRESHOLD;
    }
}