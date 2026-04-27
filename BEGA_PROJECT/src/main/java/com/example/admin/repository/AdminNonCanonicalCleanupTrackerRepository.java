package com.example.admin.repository;

import com.example.admin.entity.AdminNonCanonicalCleanupTrackerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdminNonCanonicalCleanupTrackerRepository
        extends JpaRepository<AdminNonCanonicalCleanupTrackerEntity, Long> {

    List<AdminNonCanonicalCleanupTrackerEntity> findAllByOrderByUpdatedAtDesc();

    Optional<AdminNonCanonicalCleanupTrackerEntity> findByStartDateAndEndDate(LocalDate startDate, LocalDate endDate);
}
