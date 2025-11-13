package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPost;
import com.example.demo.entity.UserEntity;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CheerPostRepo extends JpaRepository<CheerPost, Long> {
    @EntityGraph(attributePaths = {"author", "team"})
    Page<CheerPost> findByTeam_IdOrderByCreatedAtDesc(String teamId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "team"})
    @Query("SELECT p FROM CheerPost p WHERE (:teamId IS NULL OR p.team.id = :teamId) ORDER BY p.postType DESC, p.createdAt DESC")
    Page<CheerPost> findAllOrderByPostTypeAndCreatedAt(String teamId, Pageable pageable);

    /**
     * 조회수 증가 (UPDATE 쿼리만 실행)
     * 전체 엔티티를 저장하지 않고 views 필드만 업데이트하여 성능 최적화
     */
    @Modifying
    @Query("UPDATE CheerPost p SET p.views = p.views + 1 WHERE p.id = :postId")
    void incrementViewCount(@Param("postId") Long postId);
    
    @Query("SELECT COUNT(p) FROM CheerPost p WHERE p.author.id = :userId")
    int countByUserId(@Param("userId") Long userId);
    
    List<CheerPost> findAllByOrderByCreatedAtDesc();
    
    List<CheerPost> findByAuthor(UserEntity author);
}
