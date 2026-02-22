package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.ReportReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CheerReportRepo extends JpaRepository<CheerPostReport, Long> {
    List<CheerPostReport> findByPostId(Long postId);

    List<CheerPostReport> findByReporterId(Long reporterId);

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
