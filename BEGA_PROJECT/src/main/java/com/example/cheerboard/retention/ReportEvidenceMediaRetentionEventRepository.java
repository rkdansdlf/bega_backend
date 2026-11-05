package com.example.cheerboard.retention;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 추가 전용 이력 저장소. 수정과 삭제 메서드는 일부러 두지 않는다. */
public interface ReportEvidenceMediaRetentionEventRepository
        extends JpaRepository<ReportEvidenceMediaRetentionEvent, Long> {

    List<ReportEvidenceMediaRetentionEvent> findByRetentionIdOrderByIdAsc(Long retentionId);
}
