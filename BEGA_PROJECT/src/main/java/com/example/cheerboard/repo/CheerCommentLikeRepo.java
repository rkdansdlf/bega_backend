package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheerCommentLikeRepo extends JpaRepository<CheerCommentLike, CheerCommentLike.Id> {
}
