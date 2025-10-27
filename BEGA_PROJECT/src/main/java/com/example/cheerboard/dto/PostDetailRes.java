package com.example.cheerboard.dto;

import java.time.Instant;
import java.util.List;

public record PostDetailRes(Long id, String teamId, String title, String content,
                            String author, 
                            String authorEmail, 
                            Instant createdAt, 
                            int comments, int likes, boolean likedByMe, boolean isOwner, 
                            List<String> imageUrls, Integer views, String postType
                            ) {}