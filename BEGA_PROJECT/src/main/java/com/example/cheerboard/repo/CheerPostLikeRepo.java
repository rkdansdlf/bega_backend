package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.CheerPostLike.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CheerPostLikeRepo extends JpaRepository<CheerPostLike, Id> {
    long countByPostId(Long postId);
    
    /**
     * 특정 게시글의 모든 좋아요 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CheerPostLike l WHERE l.id.postId = :postId")
    void deleteByIdPostId(Long postId);
}