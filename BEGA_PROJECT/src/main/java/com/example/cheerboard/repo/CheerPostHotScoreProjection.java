package com.example.cheerboard.repo;

import java.time.Instant;

public interface CheerPostHotScoreProjection {

    Long getId();

    int getViews();

    int getLikeCount();

    int getCommentCount();

    int getRepostCount();

    Instant getCreatedAt();
}
