package com.example.cheerboard.dto;

import java.util.List;

public record CreatePostReq(String teamId,
                            String content,
                            List<String> images,
                            String postType
                            ) {}
