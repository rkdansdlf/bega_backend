package com.example.cheerboard.dto;

import java.time.Instant;

public record CommentRes(Long id, String author, String authorEmail, String authorTeamId, String content, Instant createdAt) {}