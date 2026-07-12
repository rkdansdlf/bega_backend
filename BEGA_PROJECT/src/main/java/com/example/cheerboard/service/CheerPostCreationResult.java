package com.example.cheerboard.service;

import com.example.cheerboard.dto.PostDetailRes;

public record CheerPostCreationResult(PostDetailRes post, boolean created) {
}
