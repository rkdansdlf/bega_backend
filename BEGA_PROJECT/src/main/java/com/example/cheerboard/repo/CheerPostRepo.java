package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CheerPostRepo extends JpaRepository<CheerPost, Long> {
    @EntityGraph(attributePaths = "author")
    Page<CheerPost> findByTeamIdOrderByCreatedAtDesc(String teamId, Pageable pageable);
    
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM CheerPost p WHERE (:teamId IS NULL OR p.teamId = :teamId) ORDER BY p.postType DESC, p.createdAt DESC")
    Page<CheerPost> findAllOrderByPostTypeAndCreatedAt(String teamId, Pageable pageable);
}