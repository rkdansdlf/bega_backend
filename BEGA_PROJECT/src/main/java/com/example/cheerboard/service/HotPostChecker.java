package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * HOT 게시글 판정 로직을 담당하는 클래스
 */
@Component
@RequiredArgsConstructor
public class HotPostChecker {

    private final PopularFeedScoringService popularFeedScoringService;

    /**
     * 게시글이 HOT 게시글인지 판정
     *
     * @param post 판정할 게시글
     * @return HOT 게시글 여부
     */
    public boolean isHotPost(CheerPost post) {
        return isHotPost(post, post.getViews(), Instant.now());
    }

    public boolean isHotPost(CheerPost post, int views) {
        return isHotPost(post, views, Instant.now());
    }

    public boolean isHotPost(CheerPost post, int views, Instant now) {
        return popularFeedScoringService.isHotEligible(post, views, now);
    }
}
