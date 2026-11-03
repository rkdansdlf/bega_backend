package com.example.cheerboard.service;

import com.example.cheerboard.repo.CheerPostHotScoreProjection;
import com.example.cheerboard.repo.CheerPostRepo;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheerPostViewSyncService {

    private final CheerPostRepo postRepo;

    @Transactional(readOnly = true)
    public long getViewSyncWatermark(Long postId) {
        return postRepo.findViewSyncWatermarkById(postId).orElse(0L);
    }

    @Transactional
    public Optional<CheerPostHotScoreProjection> applyBatch(Long postId, long sequence, int delta) {
        if (sequence <= 0 || delta <= 0) {
            throw new IllegalArgumentException("View sync batch sequence and delta must be positive");
        }

        postRepo.applyViewCountSyncBatch(postId, sequence, delta);
        return postRepo.findHotScoreProjectionById(postId);
    }
}
