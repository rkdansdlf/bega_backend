package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.ReportReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CheerReportRepo extends JpaRepository<CheerPostReport, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CheerPostReport r WHERE r.id = :reportId")
    java.util.Optional<CheerPostReport> findByIdForWrite(@Param("reportId") Long reportId);

    List<CheerPostReport> findByPostId(Long postId);

    List<CheerPostReport> findByReporterId(Long reporterId);

    @Query(value = """
            SELECT p.author_id
              FROM cheer_post_reports r
              JOIN cheer_post p ON p.id = r.post_id
             WHERE r.id = :reportId
            """, nativeQuery = true)
    Optional<Long> findPostAuthorIdByReportId(@Param("reportId") Long reportId);

    @Query(value = """
            SELECT DISTINCT p.id
              FROM cheer_post_reports r
              JOIN cheer_post p ON p.id = r.post_id
             WHERE p.author_id = :authorId
               AND (r.status IN ('PENDING', 'IN_REVIEW')
                    OR r.appeal_status IN ('REQUESTED', 'REVIEWING'))
            """, nativeQuery = true)
    Set<Long> findOpenModerationPostIdsByAuthorId(@Param("authorId") Long authorId);

    boolean existsByPost_IdAndReporter_IdAndCreatedAtAfter(Long postId, Long reporterId, LocalDateTime after);

    long countByReporter_IdAndCreatedAtAfter(Long reporterId, LocalDateTime after);

    /**
     * 날짜 경계는 null 을 허용하지 않는다. PostgreSQL 에서 `? IS NULL` 로 쓰인 null 타임스탬프 파라미터는
     * PgJDBC 가 타입 미지정으로 보내 "could not determine data type of parameter" 로 실패한다.
     * 호출부가 필터가 없을 때 넓은 경계 값을 넘긴다.
     */
    @Query(value = """
            SELECT r.id AS id,
                   post.id AS postId,
                   post.content AS postContent,
                   reporter.id AS reporterId,
                   reporter.handle AS reporterHandle,
                   r.reason AS reason,
                   r.description AS description,
                   r.status AS status,
                   r.adminAction AS adminAction,
                   r.adminMemo AS adminMemo,
                   r.handledBy AS handledBy,
                   r.handledAt AS handledAt,
                   r.evidenceUrl AS evidenceUrl,
                   r.requestedAction AS requestedAction,
                   r.appealStatus AS appealStatus,
                   r.appealReason AS appealReason,
                   r.appealCount AS appealCount,
                   r.createdAt AS createdAt
            FROM CheerPostReport r
            LEFT JOIN r.post post
            LEFT JOIN r.reporter reporter
            WHERE (:status IS NULL OR r.status = :status)
              AND (:reason IS NULL OR r.reason = :reason)
              AND r.createdAt >= :fromAt
              AND r.createdAt <= :toAt
            ORDER BY r.createdAt DESC, r.id DESC
            """,
            countQuery = """
                    SELECT COUNT(r) FROM CheerPostReport r
                    WHERE (:status IS NULL OR r.status = :status)
                      AND (:reason IS NULL OR r.reason = :reason)
                      AND r.createdAt >= :fromAt
                      AND r.createdAt <= :toAt
                    """)
            Page<AdminReportListProjection> findForAdmin(
            @Param("status") CheerPostReport.ReportStatus status,
            @Param("reason") ReportReason reason,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            Pageable pageable);

    @Query("""
            SELECT r.id AS id,
                   post.id AS postId,
                   post.content AS postContent,
                   reporter.id AS reporterId,
                   reporter.handle AS reporterHandle,
                   r.reason AS reason,
                   r.description AS description,
                   r.status AS status,
                   r.adminAction AS adminAction,
                   r.adminMemo AS adminMemo,
                   r.handledBy AS handledBy,
                   r.handledAt AS handledAt,
                   r.evidenceUrl AS evidenceUrl,
                   r.requestedAction AS requestedAction,
                   r.appealStatus AS appealStatus,
                   r.appealReason AS appealReason,
                   r.appealCount AS appealCount,
                   r.createdAt AS createdAt
            FROM CheerPostReport r
            LEFT JOIN r.post post
            LEFT JOIN r.reporter reporter
            WHERE r.id = :reportId
            """)
    Optional<AdminReportListProjection> findAdminReportById(@Param("reportId") Long reportId);
}
