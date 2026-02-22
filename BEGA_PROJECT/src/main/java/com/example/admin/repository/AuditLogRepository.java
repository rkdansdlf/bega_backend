package com.example.admin.repository;

import com.example.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 감사 로그 Repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 최신순으로 감사 로그 조회
     */
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    /**
     * 페이징 지원 최신순 조회
     */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 특정 관리자의 작업 로그 조회
     */
    List<AuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    /**
     * 특정 대상 사용자의 변경 이력 조회
     */
    List<AuditLog> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

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
