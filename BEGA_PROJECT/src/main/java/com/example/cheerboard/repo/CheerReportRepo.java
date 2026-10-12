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

    @Query("""
            SELECT r FROM CheerPostReport r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:reason IS NULL OR r.reason = :reason)
              AND (:fromAt IS NULL OR r.createdAt >= :fromAt)
              AND (:toAt IS NULL OR r.createdAt <= :toAt)
            ORDER BY r.createdAt DESC
            """)
    Page<CheerPostReport> findForAdmin(
            @Param("status") CheerPostReport.ReportStatus status,
            @Param("reason") ReportReason reason,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            Pageable pageable);
}
