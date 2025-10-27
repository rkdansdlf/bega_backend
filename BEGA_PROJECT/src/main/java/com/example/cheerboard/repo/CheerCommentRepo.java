package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CheerCommentRepo extends JpaRepository<CheerComment, Long> {
    @EntityGraph(attributePaths = "author")
    Page<CheerComment> findByPostIdOrderByCreatedAtDesc(Long postId, Pageable pageable);
    
    /**
     * 특정 게시글의 모든 댓글 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CheerComment c WHERE c.post.id = :postId")
    void deleteByPostId(Long postId);
}