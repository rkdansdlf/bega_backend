package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheerBookmarkRepo extends JpaRepository<CheerPostBookmark, CheerPostBookmark.Id> {
    Page<CheerPostBookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<CheerPostBookmark> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);
}
