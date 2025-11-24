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

    // gameId로 경기 조회
    Optional<Match> findByGameId(String gameId);

    // 특정 날짜의 경기 목록
    List<Match> findByGameDate(LocalDate today);

    // 오늘 이전 서로 다른 날짜 7일치 조회 (JPQL 사용)
    @Query("SELECT DISTINCT m.gameDate FROM Match m " +
           "WHERE m.gameDate < :today " +
           "AND m.homeScore IS NOT NULL " +
           "AND m.awayScore IS NOT NULL " +
           "ORDER BY m.gameDate DESC")
    List<LocalDate> findRecentDistinctGameDates(@Param("today") LocalDate today);

    // 특정 날짜들의 모든 경기 조회 (오래된 날짜부터 = ASC)
    @Query("SELECT m FROM Match m " +
           "WHERE m.gameDate IN :dates " +
           "AND m.homeScore IS NOT NULL " +
           "AND m.awayScore IS NOT NULL " +
           "ORDER BY m.gameDate ASC, m.gameId ASC")
    List<Match> findAllByGameDatesIn(@Param("dates") List<LocalDate> dates);

    // 특정 기간의 완료된 경기만
    @Query("SELECT m FROM Match m " +
           "WHERE m.gameDate BETWEEN :startDate AND :endDate " +
           "AND m.homeScore IS NOT NULL " +
           "AND m.awayScore IS NOT NULL " +
           "ORDER BY m.gameDate DESC")
    List<Match> findCompletedByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    // 더미 데이터 조회
    List<Match> findByIsDummy(Boolean isDummy);
}