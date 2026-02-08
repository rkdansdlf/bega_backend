package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheerReportRepo extends JpaRepository<CheerPostReport, Long> {
    List<CheerPostReport> findByPostId(Long postId);

    List<CheerPostReport> findByReporterId(Long reporterId);
}
