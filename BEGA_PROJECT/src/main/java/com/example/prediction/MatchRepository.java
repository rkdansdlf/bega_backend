package com.example.prediction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepository extends JpaRepository<Match, String> {

    Optional<Match> findByGameId(String gameId);

    List<Match> findByGameDate(LocalDate today);

    // 특정 날짜 + 더미 여부로 조회
    List<Match> findByGameDateAndIsDummy(LocalDate gameDate, Boolean isDummy);

    @Query("SELECT DISTINCT m.gameDate FROM Match m " +
           "WHERE m.gameDate < :today " +
           "AND m.homeScore IS NOT NULL " +
           "AND m.awayScore IS NOT NULL " +
           "ORDER BY m.gameDate DESC")
    List<LocalDate> findRecentDistinctGameDates(@Param("today") LocalDate today);

    @Query("SELECT m FROM Match m " +
           "WHERE m.gameDate IN :dates " +
           "AND m.homeScore IS NOT NULL " +
           "AND m.awayScore IS NOT NULL " +
           "ORDER BY m.gameDate ASC, m.gameId ASC")
    List<Match> findAllByGameDatesIn(@Param("dates") List<LocalDate> dates);

    @Query("SELECT m FROM Match m " +
           "WHERE m.gameDate BETWEEN :startDate AND :endDate " +
           "AND m.homeScore IS NOT NULL " +
           "AND m.awayScore IS NOT NULL " +
           "ORDER BY m.gameDate DESC")
    List<Match> findCompletedByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    List<Match> findByIsDummy(Boolean isDummy);
}