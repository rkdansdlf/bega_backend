package com.example.admin.repository;

import com.example.admin.entity.AuditLog;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 감사 로그 Repository
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update AuditLog audit
               set audit.oldValue = :redactedValue
             where audit.targetUserId = :targetUserId
               and audit.action in (:actions)
            """)
    int redactDeletedUserAuditValues(
            @Param("targetUserId") Long targetUserId,
            @Param("actions") List<AuditLog.AuditAction> actions,
            @Param("redactedValue") String redactedValue);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AuditLog audit
               set audit.description = :redactedValue
             where audit.targetUserId = :targetUserId
               and audit.action in (:actions)
            """)
    int redactDeletedUserAuditDescriptions(
            @Param("targetUserId") Long targetUserId,
            @Param("actions") List<AuditLog.AuditAction> actions,
            @Param("redactedValue") String redactedValue);

    /**
     * 페이징 지원 최신순 조회
     */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 기간별 로그 조회
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    List<AuditLog> findByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 작업 유형별 조회
     */
    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditLog.AuditAction action);
}
