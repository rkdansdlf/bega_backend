package com.example.demo.repo;

import com.example.demo.entity.TeamHistoryEntity;
import com.example.demo.entity.TeamFranchiseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 팀 히스토리 리포지토리
 *
 * <p>시즌별 팀 정보에 접근하기 위한 리포지토리입니다.</p>
 */
@Repository
public interface TeamHistoryRepository extends JpaRepository<TeamHistoryEntity, Integer> {

    /**
     * 특정 시즌의 팀 히스토리 조회
     *
     * @param season 시즌 연도
     * @return 팀 히스토리 목록
     */
    List<TeamHistoryEntity> findBySeason(Integer season);

    /**
     * 특정 시즌과 팀 코드로 팀 히스토리 조회
     *
     * @param season 시즌 연도
     * @param teamCode 팀 코드
     * @return 팀 히스토리 엔티티
     */
    Optional<TeamHistoryEntity> findBySeasonAndTeamCode(Integer season, String teamCode);

    /**
     * 프랜차이즈의 모든 히스토리 조회
     *
     * @param franchise 프랜차이즈 엔티티
     * @return 팀 히스토리 목록
     */
    List<TeamHistoryEntity> findByFranchise(TeamFranchiseEntity franchise);

    /**
     * 프랜차이즈 ID로 모든 히스토리 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @return 팀 히스토리 목록
     */
    @Query("SELECT th FROM TeamHistoryEntity th WHERE th.franchise.id = :franchiseId ORDER BY th.season DESC")
    List<TeamHistoryEntity> findByFranchiseIdOrderBySeasonDesc(@Param("franchiseId") Integer franchiseId);

    /**
     * 특정 프랜차이즈의 특정 시즌 히스토리 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @param season 시즌 연도
     * @return 팀 히스토리 엔티티
     */
    @Query("SELECT th FROM TeamHistoryEntity th WHERE th.franchise.id = :franchiseId AND th.season = :season")
    Optional<TeamHistoryEntity> findByFranchiseIdAndSeason(
        @Param("franchiseId") Integer franchiseId,
        @Param("season") Integer season
    );

    /**
     * 특정 시즌의 순위별 팀 히스토리 조회
     *
     * @param season 시즌 연도
     * @return 순위순으로 정렬된 팀 히스토리 목록
     */
    @Query("SELECT th FROM TeamHistoryEntity th WHERE th.season = :season ORDER BY th.ranking ASC")
    List<TeamHistoryEntity> findBySeasonOrderByRanking(@Param("season") Integer season);

    /**
     * 특정 연도 범위의 팀 히스토리 조회
     *
     * @param startYear 시작 연도
     * @param endYear 종료 연도
     * @return 팀 히스토리 목록
     */
    @Query("SELECT th FROM TeamHistoryEntity th WHERE th.season BETWEEN :startYear AND :endYear ORDER BY th.season DESC, th.ranking ASC")
    List<TeamHistoryEntity> findBySeasonBetween(
        @Param("startYear") Integer startYear,
        @Param("endYear") Integer endYear
    );

    /**
     * 특정 구장에서 홈 경기를 치른 팀들 조회
     *
     * @param stadium 구장 이름
     * @return 팀 히스토리 목록
     */
    List<TeamHistoryEntity> findByStadium(String stadium);

    /**
     * 팀 코드로 모든 히스토리 조회 (시즌 내림차순)
     *
     * @param teamCode 팀 코드
     * @return 팀 히스토리 목록
     */
    @Query("SELECT th FROM TeamHistoryEntity th WHERE th.teamCode = :teamCode ORDER BY th.season DESC")
    List<TeamHistoryEntity> findByTeamCodeOrderBySeasonDesc(@Param("teamCode") String teamCode);

    /**
     * 최근 N개 시즌의 팀 히스토리 조회
     *
     * @param limit 조회할 개수
     * @return 팀 히스토리 목록
     */
    @Query(value = "SELECT th FROM TeamHistoryEntity th ORDER BY th.season DESC")
    List<TeamHistoryEntity> findRecentSeasons(@Param("limit") int limit);

    /**
     * 프랜차이즈의 특정 시즌 이후 히스토리 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @param startSeason 시작 시즌
     * @return 팀 히스토리 목록 (시즌 내림차순)
     */
    @Query("SELECT th FROM TeamHistoryEntity th WHERE th.franchise.id = :franchiseId AND th.season >= :startSeason ORDER BY th.season DESC")
    List<TeamHistoryEntity> findByFranchiseIdAndSeasonGreaterThanEqualOrderBySeasonDesc(
        @Param("franchiseId") Integer franchiseId,
        @Param("startSeason") Integer startSeason
    );
}
