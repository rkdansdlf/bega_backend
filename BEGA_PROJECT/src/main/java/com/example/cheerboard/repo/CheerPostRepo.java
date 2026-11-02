package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPost.RepostType;
import com.example.auth.entity.UserEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CheerPostRepo extends JpaRepository<CheerPost, Long>, JpaSpecificationExecutor<CheerPost> {
        @Override
        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        Page<CheerPost> findAll(Specification<CheerPost> spec, Pageable pageable);

        @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM CheerPost p WHERE p.id = :postId")
        Optional<CheerPost> findByIdForWrite(@Param("postId") Long postId);

        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        Page<CheerPost> findByTeam_TeamIdOrderByCreatedAtDesc(String teamId, Pageable pageable);

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

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE CheerPost p SET p.views = p.views + :delta, p.viewSyncWatermark = :sequence "
                        + "WHERE p.id = :postId AND p.viewSyncWatermark < :sequence")
        int applyViewCountSyncBatch(
                        @Param("postId") Long postId,
                        @Param("sequence") long sequence,
                        @Param("delta") int delta);

        @Query("SELECT p.viewSyncWatermark FROM CheerPost p WHERE p.id = :postId")
        Optional<Long> findViewSyncWatermarkById(@Param("postId") Long postId);

        @Query("SELECT p.likeCount FROM CheerPost p WHERE p.id = :postId")
        Integer findLikeCountById(@Param("postId") Long postId);

        @Query("""
                        SELECT
                            p.id AS id,
                            p.views AS views,
                            p.likeCount AS likeCount,
                            p.commentCount AS commentCount,
                            p.repostCount AS repostCount,
                            p.createdAt AS createdAt
                        FROM CheerPost p
                        WHERE p.id = :postId
                        """)
        Optional<CheerPostHotScoreProjection> findHotScoreProjectionById(@Param("postId") Long postId);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
        void incrementLikeCount(@Param("postId") Long postId);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :postId")
        void decrementLikeCount(@Param("postId") Long postId);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.likeCount = :count WHERE p.id = :postId")
        void setExactLikeCount(@Param("postId") Long postId, @Param("count") int count);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
        void incrementCommentCount(@Param("postId") Long postId);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.commentCount = CASE WHEN p.commentCount > 0 THEN p.commentCount - 1 ELSE 0 END WHERE p.id = :postId")
        void decrementCommentCount(@Param("postId") Long postId);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.commentCount = :count WHERE p.id = :postId")
        void setExactCommentCount(@Param("postId") Long postId, @Param("count") int count);

        @Query("SELECT p.repostCount FROM CheerPost p WHERE p.id = :postId")
        Integer findRepostCountById(@Param("postId") Long postId);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.repostCount = p.repostCount + 1 WHERE p.id = :postId")
        void incrementRepostCount(@Param("postId") Long postId);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE CheerPost p SET p.repostCount = CASE WHEN p.repostCount > 0 THEN p.repostCount - 1 ELSE 0 END WHERE p.id = :postId")
        void decrementRepostCount(@Param("postId") Long postId);

        @Query("SELECT COUNT(p) FROM CheerPost p WHERE p.author.id = :userId")
        int countByUserId(@Param("userId") Long userId);

        @Query("""
                        SELECT p.author.id AS authorId, COUNT(p.id) AS postCount
                        FROM CheerPost p
                        WHERE p.author.id IN :authorIds
                        GROUP BY p.author.id
                        """)
        List<CheerPostAuthorCountProjection> findPostCountsByAuthorIds(
                        @Param("authorIds") Collection<Long> authorIds);

        long countByAuthor_IdAndSourceUrlAndCreatedAtAfter(Long authorId, String sourceUrl, java.time.Instant after);

        @EntityGraph(attributePaths = { "author", "team", "repostOf", "repostOf.author", "repostOf.team" })
        Page<CheerPost> findByAuthor_HandleOrderByCreatedAtDesc(String handle, Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team" })
        List<CheerPost> findAllByOrderByCreatedAtDesc();

        List<CheerPost> findByAuthor(UserEntity author);

        List<CheerPost> findAllByAuthor_Id(Long authorId);

        @Query(value = """
                        SELECT * FROM cheer_post
                        WHERE author_id = :authorId
                        ORDER BY id
                        FOR UPDATE
                        """, nativeQuery = true)
        List<CheerPost> findAllByAuthorIdIncludingDeletedForUpdate(@Param("authorId") Long authorId);

        @EntityGraph(attributePaths = { "author", "team" })
        Optional<CheerPost> findFirstByDiaryIdAndDeletedFalse(Long diaryId);

        @EntityGraph(attributePaths = { "author", "team" })
        Optional<CheerPost> findFirstByPartyIdAndDeletedFalse(Long partyId);

        boolean existsByPartyId(Long partyId);

        @Query(value = """
                        SELECT *
                        FROM cheer_post
                        WHERE LOWER(TRIM(CAST(deleted AS CHAR(5)))) IN ('1','t','true')
                        """, nativeQuery = true)
        @Transactional(readOnly = true)
        List<CheerPost> findSoftDeletedPosts();

        @Query(value = "SELECT author_id FROM cheer_post WHERE id = :id", nativeQuery = true)
        Optional<Long> findPurgeAuthorId(@Param("id") Long id);

        /** Started purges that still block the author: all without reports, and reported ones within the hold limit. */
        @Query(value = """
                        SELECT p.id FROM cheer_post p
                         WHERE p.author_id = :authorId AND p.purge_started_at IS NOT NULL
                           AND NOT (p.purge_started_at <= :staleBefore AND EXISTS (
                             SELECT 1 FROM cheer_post_reports r WHERE r.post_id = p.id))
                         ORDER BY p.id
                        """, nativeQuery = true)
        List<Long> findStartedPurgeIdsByAuthor(@Param("authorId") Long authorId,
                                               @Param("staleBefore") java.time.LocalDateTime staleBefore,
                                               Pageable pageable);

        /**
         * True when any report was ever filed on this post. A reported repost is soft deleted instead of hard deleted,
         * so the cascade on {@code cheer_post_reports} never erases the moderation record.
         */
        @Query(value = """
                        SELECT EXISTS (SELECT 1 FROM cheer_post_reports r WHERE r.post_id = :postId)
                        """, nativeQuery = true)
        boolean existsReportByPostId(@Param("postId") Long postId);

        /** True when this post has a report or appeal still awaiting a decision. Deleting the post cascades to its reports. */
        @Query(value = """
                        SELECT EXISTS (
                            SELECT 1
                              FROM cheer_post_reports r
                             WHERE r.post_id = :postId
                               AND (r.status IN ('PENDING', 'IN_REVIEW')
                                    OR r.appeal_status IN ('REQUESTED', 'REVIEWING')))
                        """, nativeQuery = true)
        boolean existsOpenModerationByPostId(@Param("postId") Long postId);

        @Query(value = """
                        SELECT p.id FROM cheer_post p
                        WHERE LOWER(TRIM(CAST(p.deleted AS CHAR(5)))) IN ('1','t','true')
                          AND NOT EXISTS (SELECT 1 FROM cheer_post_reports rr WHERE rr.post_id = p.id)
                          AND (p.purge_retry_at IS NULL OR p.purge_retry_at <= :now)
                          AND (p.purge_lease_until IS NULL OR p.purge_lease_until <= :now)
                          AND ((:started = 0 AND p.purge_started_at IS NULL)
                               OR (:started = 1 AND p.purge_started_at IS NOT NULL))
                          AND (p.purge_started_at IS NOT NULL OR NOT EXISTS (
                            SELECT 1 FROM users pending_owner WHERE pending_owner.id = p.author_id
                              AND LOWER(TRIM(CAST(pending_owner.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                              AND pending_owner.deletion_scheduled_for IS NOT NULL
                          ))
                          AND NOT EXISTS (
                            SELECT 1 FROM cheer_post evidence
                            JOIN cheer_post_reports r ON r.post_id = evidence.id
                            WHERE evidence.id = p.id
                              AND (r.status IN ('PENDING','IN_REVIEW') OR r.appeal_status IN ('REQUESTED','REVIEWING'))
                          )
                          AND (p.purge_started_at IS NOT NULL OR NOT EXISTS (
                            SELECT 1 FROM media_asset_links l JOIN media_assets a ON a.id = l.asset_id
                            JOIN users asset_owner ON asset_owner.id = a.owner_user_id
                            WHERE l.domain = 'CHEER' AND l.entity_id = p.id
                              AND LOWER(TRIM(CAST(asset_owner.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                              AND asset_owner.deletion_scheduled_for IS NOT NULL
                          ))
                          AND NOT EXISTS (
                            SELECT 1 FROM media_asset_links l JOIN media_assets a ON a.id = l.asset_id
                            JOIN users asset_owner ON asset_owner.id = a.owner_user_id
                            JOIN cheer_post evidence ON evidence.author_id = asset_owner.id
                            JOIN cheer_post_reports r ON r.post_id = evidence.id
                            WHERE l.domain = 'CHEER' AND l.entity_id = p.id
                              AND LOWER(TRIM(CAST(asset_owner.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                              AND (r.status IN ('PENDING','IN_REVIEW') OR r.appeal_status IN ('REQUESTED','REVIEWING'))
                          )
                        ORDER BY COALESCE(p.purge_retry_at, p.createdat), p.id
                        """, nativeQuery = true)
        @Transactional(readOnly = true)
        List<Long> findDuePurgeIds(@Param("now") java.time.LocalDateTime now,
                                   @Param("started") int started, Pageable pageable);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query(value = """
                        UPDATE cheer_post p
                           SET purge_started_at = COALESCE(purge_started_at, :now),
                               purge_claim_token = :token, purge_lease_until = :leaseUntil,
                               purge_attempt_count = purge_attempt_count + 1
                         WHERE p.id = :id
                           AND LOWER(TRIM(CAST(p.deleted AS CHAR(5)))) IN ('1','t','true')
                          AND NOT EXISTS (SELECT 1 FROM cheer_post_reports rr WHERE rr.post_id = p.id)
                           AND (p.purge_retry_at IS NULL OR p.purge_retry_at <= :now)
                           AND (p.purge_lease_until IS NULL OR p.purge_lease_until <= :now)
                           AND (p.purge_started_at IS NOT NULL OR NOT EXISTS (
                             SELECT 1 FROM users pending_owner WHERE pending_owner.id = p.author_id
                               AND LOWER(TRIM(CAST(pending_owner.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                               AND pending_owner.deletion_scheduled_for IS NOT NULL
                           ))
                           AND NOT EXISTS (
                             SELECT 1 FROM cheer_post evidence
                             JOIN cheer_post_reports r ON r.post_id = evidence.id
                             WHERE evidence.id = p.id
                               AND (r.status IN ('PENDING','IN_REVIEW') OR r.appeal_status IN ('REQUESTED','REVIEWING'))
                           )
                           AND (p.purge_started_at IS NOT NULL OR NOT EXISTS (
                             SELECT 1 FROM media_asset_links l JOIN media_assets a ON a.id = l.asset_id
                             JOIN users asset_owner ON asset_owner.id = a.owner_user_id
                             WHERE l.domain = 'CHEER' AND l.entity_id = p.id
                               AND LOWER(TRIM(CAST(asset_owner.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                               AND asset_owner.deletion_scheduled_for IS NOT NULL
                           ))
                           AND NOT EXISTS (
                             SELECT 1 FROM media_asset_links l JOIN media_assets a ON a.id = l.asset_id
                             JOIN users asset_owner ON asset_owner.id = a.owner_user_id
                             JOIN cheer_post evidence ON evidence.author_id = asset_owner.id
                             JOIN cheer_post_reports r ON r.post_id = evidence.id
                             WHERE l.domain = 'CHEER' AND l.entity_id = p.id
                               AND LOWER(TRIM(CAST(asset_owner.pending_deletion AS CHAR(5)))) IN ('1','t','true')
                               AND (r.status IN ('PENDING','IN_REVIEW') OR r.appeal_status IN ('REQUESTED','REVIEWING'))
                           )
                        """, nativeQuery = true)
        int claimPurge(@Param("id") Long id, @Param("token") String token,
                       @Param("now") java.time.LocalDateTime now,
                       @Param("leaseUntil") java.time.LocalDateTime leaseUntil);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query(value = """
                        UPDATE cheer_post SET purge_retry_at = :retryAt
                         WHERE id = :id
                           AND LOWER(TRIM(CAST(deleted AS CHAR(5)))) IN ('1','t','true')
                           AND (purge_retry_at IS NULL OR purge_retry_at <= :now)
                           AND (purge_lease_until IS NULL OR purge_lease_until <= :now)
                        """, nativeQuery = true)
        int deferUnclaimedPurge(@Param("id") Long id, @Param("now") java.time.LocalDateTime now,
                                @Param("retryAt") java.time.LocalDateTime retryAt);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query(value = """
                        UPDATE cheer_post SET purge_claim_token = NULL, purge_lease_until = NULL,
                               purge_retry_at = :retryAt
                         WHERE id = :id AND purge_claim_token = :token
                        """, nativeQuery = true)
        int releasePurgeForRetry(@Param("id") Long id, @Param("token") String token,
                                 @Param("retryAt") java.time.LocalDateTime retryAt);

        @Query(value = """
                        SELECT id FROM cheer_post WHERE id = :id AND purge_claim_token = :token
                          AND LOWER(TRIM(CAST(deleted AS CHAR(5)))) IN ('1','t','true') FOR UPDATE
                        """, nativeQuery = true)
        Optional<Long> lockClaimedPurge(@Param("id") Long id, @Param("token") String token);

        @Query(value = "SELECT * FROM cheer_post WHERE id = :id FOR UPDATE", nativeQuery = true)
        Optional<CheerPost> findIncludingDeletedByIdForUpdate(@Param("id") Long id);

        /**
         * Never deletes a post that has report history, so the cascade on {@code cheer_post_reports} cannot run.
         * Returns the number of rows deleted: 0 means the post was kept because it has report history.
         */
        @Modifying
        @Query(value = """
                        DELETE FROM cheer_post p WHERE p.id = :id
                           AND NOT EXISTS (SELECT 1 FROM cheer_post_reports r WHERE r.post_id = p.id)
                        """, nativeQuery = true)
        int hardDeleteById(@Param("id") Long id);

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

        @EntityGraph(attributePaths = { "author", "team" })
        @Query("SELECT p FROM CheerPost p WHERE p.id > :sinceId AND (p.repostType IS NULL OR p.repostType != 'SIMPLE') ORDER BY p.id ASC")
        List<CheerPost> findNewPostsSinceOrderByIdAsc(
                        @Param("sinceId") Long sinceId,
                        Pageable pageable);

        @EntityGraph(attributePaths = { "author", "team" })
        @Query("SELECT p FROM CheerPost p WHERE p.id > :sinceId AND p.team.teamId = :teamId AND (p.repostType IS NULL OR p.repostType != 'SIMPLE') ORDER BY p.id ASC")
        List<CheerPost> findNewTeamPostsSinceOrderByIdAsc(
                        @Param("sinceId") Long sinceId,
                        @Param("teamId") String teamId,
                        Pageable pageable);

        /**
         * 가장 최근 게시글 ID 조회 (폴링용)
         * - 단순 리포스트 제외
         * - 팀 필터링 선택적 지원
         */
        @Query("SELECT MAX(p.id) FROM CheerPost p WHERE (:teamId IS NULL OR p.team.teamId = :teamId) AND (p.repostType IS NULL OR p.repostType != 'SIMPLE')")
        Long findLatestPostId(@Param("teamId") String teamId);
}
