package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostRepost;
import com.example.cheerboard.domain.CheerPostRepost.Id;
import com.example.auth.entity.UserEntity;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CheerPostRepostRepo extends JpaRepository<CheerPostRepost, Id> {
    long countByPostId(Long postId);

    /**
     * 특정 게시글의 모든 리포스트 삭제
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CheerPostRepost r WHERE r.id.postId = :postId")
    void deleteByIdPostId(Long postId);

    List<CheerPostRepost> findByUser(UserEntity user);

    List<CheerPostRepost> findByUserIdAndPostIdIn(Long userId, Collection<Long> postIds);
}
