package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;

public record CheerPostCreationOutcome(CheerPost post, boolean created) {
}
