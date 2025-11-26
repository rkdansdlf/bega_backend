package com.example.cheerboard.dto;

import java.time.Instant;
import java.util.List;

public record CommentRes(
    Long id,
    String author,
    String authorEmail,
    String authorTeamId,
    String authorProfileImageUrl,
    String content,
    Instant createdAt,
    int likeCount,
    boolean likedByMe,
    List<CommentRes> replies  // 대댓글 목록
) {}