package com.example.cheerboard.dto;

import java.util.List;

public record CreatePostReq(String teamId, 
                            String title, 
                            String content, 
                            List<String> images,
                            String postType
                            ) {}