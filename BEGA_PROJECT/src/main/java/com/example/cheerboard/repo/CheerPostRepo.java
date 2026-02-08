package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPost.RepostType;
import com.example.auth.entity.UserEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CheerPostRepo extends JpaRepository<CheerPost, Long> {
        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        Page<CheerPost> findByTeam_TeamIdOrderByCreatedAtDesc(String teamId, Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        @Query("SELECT p FROM CheerPost p WHERE (:teamId IS NULL OR p.team.teamId = :teamId) " +
                        "AND (:postType IS NULL OR p.postType = :postType) " +
                        "AND (p.repostType IS NULL OR p.repostType != 'SIMPLE') " +
                        "AND (COALESCE(:excludedIds, NULL) IS NULL OR p.author.id NOT IN :excludedIds) " +
                        "ORDER BY CASE WHEN p.postType = 'NOTICE' AND p.createdAt > :cutoffDate THEN 0 ELSE 1 END, p.createdAt DESC")
        Page<CheerPost> findAllOrderByPostTypeAndCreatedAt(@Param("teamId") String teamId,
                        @Param("postType") com.example.cheerboard.domain.PostType postType,
                        @Param("cutoffDate") java.time.Instant cutoffDate,
                        @Param("excludedIds") Collection<Long> excludedIds,
                        Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        @Query("SELECT p FROM CheerPost p WHERE LOWER(CAST(p.content AS String)) LIKE LOWER(CONCAT('%', :q, '%')) " +
                        "AND (:teamId IS NULL OR p.team.teamId = :teamId) " +
                        "AND (p.repostType IS NULL OR p.repostType != 'SIMPLE') " +
                        "AND (COALESCE(:excludedIds, NULL) IS NULL OR p.author.id NOT IN :excludedIds)")
        Page<CheerPost> search(@Param("q") String q, @Param("teamId") String teamId,
                        @Param("excludedIds") Collection<Long> excludedIds, Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        @Query("SELECT p FROM CheerPost p WHERE (:teamId IS NULL OR p.team.teamId = :teamId) " +
                        "AND (:postType IS NULL OR p.postType = :postType) " +
                        "AND (p.repostType IS NULL OR p.repostType != 'SIMPLE') " +
                        "AND (COALESCE(:excludedIds, NULL) IS NULL OR p.author.id NOT IN :excludedIds)")
        Page<CheerPost> findByTeamIdAndPostType(@Param("teamId") String teamId,
                        @Param("postType") com.example.cheerboard.domain.PostType postType,
                        @Param("excludedIds") Collection<Long> excludedIds, Pageable pageable);

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

        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        Page<CheerPost> findByAuthor_HandleOrderByCreatedAtDesc(String handle, Pageable pageable);

        List<CheerPost> findAllByOrderByCreatedAtDesc();

        List<CheerPost> findByAuthor(UserEntity author);

        @Query(value = "SELECT * FROM cheer_post WHERE deleted = true", nativeQuery = true)
        List<CheerPost> findSoftDeletedPosts();

        @Modifying
        @Query(value = "DELETE FROM cheer_post WHERE id = :id", nativeQuery = true)
        void hardDeleteById(@Param("id") Long id);

        /**
         * 팔로우한 유저들의 게시글 조회 (팔로우 피드용)
         */
        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        @Query("SELECT p FROM CheerPost p WHERE p.author.id IN :authorIds ORDER BY p.createdAt DESC")
        Page<CheerPost> findByAuthorIdIn(@Param("authorIds") List<Long> authorIds, Pageable pageable);

        /**
         * 팔로우한 유저들의 게시글 조회 (차단 유저 제외)
         */
        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        @Query("SELECT p FROM CheerPost p WHERE p.author.id IN :authorIds AND p.author.id NOT IN :blockedIds ORDER BY p.createdAt DESC")
        Page<CheerPost> findByAuthorIdInAndAuthorIdNotIn(
                        @Param("authorIds") List<Long> authorIds,
                        @Param("blockedIds") List<Long> blockedIds,
                        Pageable pageable);

        /**
         * ID 목록으로 게시글 조회 (author, team, repostOf 체인 포함 EntityGraph)
         */
        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        @Query("SELECT p FROM CheerPost p WHERE p.id IN :ids")
        List<CheerPost> findAllByIdWithGraph(@Param("ids") Collection<Long> ids);

        /**
         * 특정 사용자가 특정 게시글에 대해 특정 타입의 리포스트를 했는지 확인
         * (단순 리포스트 토글 시 기존 리포스트 찾기용)
         */
        @Query("SELECT p FROM CheerPost p WHERE p.author = :author AND p.repostOf = :repostOf AND p.repostType = :repostType")
        Optional<CheerPost> findByAuthorAndRepostOfAndRepostType(
                        @Param("author") UserEntity author,
                        @Param("repostOf") CheerPost repostOf,
                        @Param("repostType") RepostType repostType);

        /**
         * 특정 사용자가 특정 게시글에 대해 단순 리포스트를 했는지 확인
         */
        boolean existsByAuthorAndRepostOfAndRepostType(UserEntity author, CheerPost repostOf, RepostType repostType);

        /**
         * 특정 ID 이후에 생성된 게시글 수 카운트 (폴링용)
         * - 단순 리포스트 제외
         * - 팀 필터링 선택적 지원
         */
        @Query("SELECT COUNT(p) FROM CheerPost p WHERE p.id > :sinceId AND (:teamId IS NULL OR p.team.teamId = :teamId) AND (p.repostType IS NULL OR p.repostType != 'SIMPLE')")
        int countNewPostsSince(@Param("sinceId") Long sinceId, @Param("teamId") String teamId);

        /**
         * 가장 최근 게시글 ID 조회 (폴링용)
         * - 단순 리포스트 제외
         * - 팀 필터링 선택적 지원
         */
        @Query("SELECT MAX(p.id) FROM CheerPost p WHERE (:teamId IS NULL OR p.team.teamId = :teamId) AND (p.repostType IS NULL OR p.repostType != 'SIMPLE')")
        Long findLatestPostId(@Param("teamId") String teamId);
}
