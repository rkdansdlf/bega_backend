package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * GameEntity - 통합 경기 엔티티
 *
 * 기존 Match, BegaGame, HomePageGame을 통합한 엔티티입니다.
 * game 테이블의 PK는 id(bigint)이며, game_id는 unique key입니다.
 *
 * 통합 내역:
 * - Match.java (prediction 패키지) - 예측 시스템용
 * - BegaGame.java (BegaDiary 패키지) - 일기 시스템용
 * - HomePageGame.java (homePage 패키지) - 홈페이지용
 */
@Entity
@Table(name = "game")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameEntity {

    // ========================================
    // Primary Key
    // ========================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========================================
    // 기본 경기 정보
    // ========================================

    @Column(name = "game_id", unique = true, nullable = false, length = 20)
    private String gameId;

    @Column(name = "game_date")
    private LocalDate gameDate;

    @Column(name = "stadium", length = 50)
    private String stadium;

    @Column(name = "home_team", length = 20)
    private String homeTeam;

    @Column(name = "away_team", length = 20)
    private String awayTeam;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "winning_team", length = 20)
    private String winningTeam;

    @Column(name = "winning_score")
    private Integer winningScore;

    // ========================================
    // 시즌 및 상태 정보
    // ========================================

    @Column(name = "season_id")
    private Integer seasonId;

    @Column(name = "stadium_id")
    private Integer stadiumId;

    @Column(name = "game_status", length = 20)
    private String gameStatus; // SCHEDULED, COMPLETED, CANCELLED 등

    @Column(name = "is_dummy")
    private Boolean isDummy;

    // ========================================
    // 선발 투수 정보 (실제 DB 컬럼)
    // ========================================

    @Column(name = "home_pitcher", length = 30)
    private String homePitcher;

    @Column(name = "away_pitcher", length = 30)
    private String awayPitcher;

    // ========================================
    // 비즈니스 로직 메서드
    // ========================================

    /**
     * 승리팀 계산 (스코어가 있는 경우만)
     */
    public String getWinner() {
        if (homeScore == null || awayScore == null) {
            return null; // 경기 미종료
        }
        if (homeScore.equals(awayScore)) {
            return "draw";
        }
        return homeScore > awayScore ? "home" : "away";
    }

    /**
     * 경기 종료 여부 (스코어 유무로 판단)
     */
    public boolean isFinished() {
        return homeScore != null && awayScore != null;
    }

    /**
     * 스코어 문자열 반환 (5-3 형식)
     */
    public String getScoreString() {
        if (homeScore != null && awayScore != null) {
            return homeScore + "-" + awayScore;
        }
        return null;
    }

    /**
     * 경기 상태가 완료인지 확인
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(gameStatus);
    }

    /**
     * 경기 상태가 예정인지 확인
     */
    public boolean isScheduled() {
        return "SCHEDULED".equals(gameStatus);
    }

    /**
     * 더미 경기인지 확인
     */
    public boolean isDummyGame() {
        return Boolean.TRUE.equals(isDummy);
    }
}
