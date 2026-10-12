package com.example.leaderboard.repository;

import com.example.leaderboard.entity.LegacyPerfectDayDate;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyPerfectDayDateRepository extends JpaRepository<LegacyPerfectDayDate, LocalDate> {
}
