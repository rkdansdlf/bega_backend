package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPost;
import com.example.auth.entity.UserEntity;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CheerPostRepo extends JpaRepository<CheerPost, Long> {
        @EntityGraph(attributePaths = { "author", "team" })
        Page<CheerPost> findByTeam_TeamIdOrderByCreatedAtDesc(String teamId, Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team" })
        @Query("SELECT p FROM CheerPost p WHERE (:teamId IS NULL OR p.team.teamId = :teamId) AND (:postType IS NULL OR p.postType = :postType) ORDER BY CASE WHEN p.postType = 'NOTICE' AND p.createdAt > :cutoffDate THEN 0 ELSE 1 END, p.createdAt DESC")
        Page<CheerPost> findAllOrderByPostTypeAndCreatedAt(@Param("teamId") String teamId,
                        @Param("postType") com.example.cheerboard.domain.PostType postType,
                        @Param("cutoffDate") java.time.Instant cutoffDate,
                        Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team" })
        @Query("SELECT p FROM CheerPost p WHERE (LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))) AND (:teamId IS NULL OR p.team.teamId = :teamId)")
        Page<CheerPost> search(@Param("q") String q, @Param("teamId") String teamId, Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team" })
        @Query("SELECT p FROM CheerPost p WHERE (:teamId IS NULL OR p.team.teamId = :teamId) AND (:postType IS NULL OR p.postType = :postType)")
        Page<CheerPost> findByTeamIdAndPostType(@Param("teamId") String teamId,
                        @Param("postType") com.example.cheerboard.domain.PostType postType, Pageable pageable);

        /**
         * 조회수 증가 (UPDATE 쿼리만 실행)
         * 전체 엔티티를 저장하지 않고 views 필드만 업데이트하여 성능 최적화
         */
        @Modifying
        @Query("UPDATE CheerPost p SET p.views = p.views + 1 WHERE p.id = :postId")
        void incrementViewCount(@Param("postId") Long postId);

        @Modifying
        @Query("UPDATE CheerPost p SET p.views = p.views + :delta WHERE p.id = :postId")
        void incrementViewCountByDelta(@Param("postId") Long postId, @Param("delta") int delta);

        @Query("SELECT COUNT(p) FROM CheerPost p WHERE p.author.id = :userId")
        int countByUserId(@Param("userId") Long userId);

        @EntityGraph(attributePaths = { "author", "team" })
        Page<CheerPost> findByAuthor_HandleOrderByCreatedAtDesc(String handle, Pageable pageable);

        List<CheerPost> findAllByOrderByCreatedAtDesc();

        List<CheerPost> findByAuthor(UserEntity author);

        @Query(value = "SELECT * FROM cheer_post WHERE deleted = true", nativeQuery = true)
        List<CheerPost> findSoftDeletedPosts();

        @Modifying
        @Query(value = "DELETE FROM cheer_post WHERE id = :id", nativeQuery = true)
        void hardDeleteById(@Param("id") Long id);
}
