package com.example.demo.repo;

import com.example.demo.entity.TeamEntity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 팀 리포지토리
 *
 * <p>팀 데이터 조회 및 관리</p>
 * <p>is_active 필드를 활용한 현재/과거 구단 구분 쿼리 제공</p>
 *
 * @see TeamEntity
 */
@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, String> {

    /**
     * 팀 ID로 조회
     * @param teamId 팀 ID (예: SS, LOT, KIA)
     * @return Optional 팀 엔티티
     */
    Optional<TeamEntity> findByTeamId(String teamId);

    // =========================
    // is_active 기반 조회
    // =========================

    /**
     * 현재 운영 중인 KBO 구단 10개 조회
     * <p>is_active = true인 팀만 반환</p>
     *
     * <p>Usage:</p>
     * <pre>
     * // API: 현재 구단 목록
     * {@literal @}GetMapping("/api/teams/active")
     * public List<TeamDto> getActiveTeams() {
     *     return teamRepository.findByIsActiveTrue()
     *         .stream()
     *         .map(TeamDto::from)
     *         .collect(Collectors.toList());
     * }
     * </pre>
     *
     * @return 현재 운영 중인 10개 구단
     */
    List<TeamEntity> findByIsActiveTrue();

    /**
     * 과거 구단 및 국제대회 팀 조회
     * <p>is_active = false인 팀 반환</p>
     * @return 비활성 팀 목록 (과거 구단 + 국제팀)
     */
    List<TeamEntity> findByIsActiveFalse();

    /**
     * is_active 상태로 조회
     * @param isActive true: 현재 구단, false: 과거/국제팀
     * @return 팀 목록
     */
    List<TeamEntity> findByIsActive(Boolean isActive);

    // =========================
    // franchise_id 기반 조회
    // =========================

    /**
     * 특정 프랜차이즈의 팀 조회
     * <p>franchise_id로 모든 팀 검색 (현재 + 과거)</p>
     *
     * <p>Example:</p>
     * <pre>
     * // franchise_id = 5 (KIA)
     * // Returns: [KIA (active), HT (inactive)]
     * teamRepository.findByFranchiseId(5);
     * </pre>
     *
     * @param franchiseId 프랜차이즈 ID
     * @return 해당 프랜차이즈의 모든 팀 (현재 + 과거)
     */
    List<TeamEntity> findByFranchiseId(Integer franchiseId);

    /**
     * 특정 프랜차이즈의 현재 활성 팀만 조회
     * @param franchiseId 프랜차이즈 ID
     * @return 활성 팀 (보통 1개)
     */
    List<TeamEntity> findByFranchiseIdAndIsActiveTrue(Integer franchiseId);

    /**
     * 프랜차이즈의 과거 팀만 조회
     * @param franchiseId 프랜차이즈 ID
     * @return 과거 팀 목록
     */
    List<TeamEntity> findByFranchiseIdAndIsActiveFalse(Integer franchiseId);

    /**
     * 프랜차이즈의 모든 팀 조회 (활성 우선, 창단연도 순)
     *
     * <p>Custom Query로 정렬 기준 명확화</p>
     *
     * @param franchiseId 프랜차이즈 ID
     * @return is_active DESC, founded_year ASC 정렬
     */
    @Query("SELECT t FROM TeamEntity t " +
           "WHERE t.franchiseId = :franchiseId " +
           "ORDER BY t.isActive DESC, t.foundedYear ASC")
    List<TeamEntity> findByFranchiseIdWithHistory(@Param("franchiseId") Integer franchiseId);

    // =========================
    // 별칭(alias) 검색
    // =========================

    /**
     * 팀 ID 또는 별칭으로 검색 (PostgreSQL array 연산)
     *
     * <p>PostgreSQL ANY 연산자 사용:</p>
     * <pre>
     * WHERE team_id = 'OB' OR '두산' = ANY(aliases)
     * </pre>
     *
     * <p>Usage:</p>
     * <pre>
     * // "두산"으로 검색 → OB 팀 반환
     * teamRepository.findByTeamIdOrAlias("두산");
     *
     * // "해태"로 검색 → KIA 팀 반환
     * teamRepository.findByTeamIdOrAlias("해태");
     * </pre>
     *
     * <p><strong>Important:</strong> PostgreSQL text[] 배열 타입 필요</p>
     * <p>aliases 컬럼이 text[] 타입이어야 ANY 연산자 작동</p>
     *
     * @param code 팀 코드 또는 별칭 (예: OB, 두산, DO)
     * @return Optional 팀 엔티티
     */
    @Query(value = "SELECT * FROM teams t " +
                   "WHERE t.team_id = :code OR :code = ANY(t.aliases)",
           nativeQuery = true)
    Optional<TeamEntity> findByTeamIdOrAlias(@Param("code") String code);

    /**
     * 여러 코드/별칭으로 검색
     * @param codes 코드 목록
     * @return 매칭되는 팀 목록
     */
    @Query(value = "SELECT * FROM teams t " +
                   "WHERE t.team_id = ANY(:codes) " +
                   "OR EXISTS (SELECT 1 FROM unnest(t.aliases) alias WHERE alias = ANY(:codes))",
           nativeQuery = true)
    List<TeamEntity> findByTeamIdOrAliasIn(@Param("codes") List<String> codes);

    // =========================
    // 국제대회/특수 팀 조회
    // =========================

    /**
     * 국제대회 팀 조회
     * <p>franchise_id IS NULL인 팀</p>
     * @return 국제대회 팀 + 특수 팀 (ALLSTAR 등)
     */
    List<TeamEntity> findByFranchiseIdIsNull();

    /**
     * KBO 프랜차이즈 팀만 조회 (국제팀 제외)
     * <p>franchise_id IS NOT NULL인 팀</p>
     * @return KBO 구단 (현재 + 과거)
     */
    List<TeamEntity> findByFranchiseIdIsNotNull();

    // =========================
    // 통계 및 검증 쿼리
    // =========================

    /**
     * 활성 팀 개수 조회
     * <p>정상 상태: 10개</p>
     * @return 활성 팀 수
     */
    @Query("SELECT COUNT(t) FROM TeamEntity t WHERE t.isActive = true")
    long countActiveTeams();

    /**
     * 프랜차이즈별 활성 팀 수 확인
     * <p>정상 상태: 각 프랜차이즈당 1개</p>
     * @param franchiseId 프랜차이즈 ID
     * @return 활성 팀 수 (should be 1)
     */
    @Query("SELECT COUNT(t) FROM TeamEntity t WHERE t.franchiseId = :franchiseId AND t.isActive = true")
    long countActiveTeamsByFranchise(@Param("franchiseId") Integer franchiseId);

    /**
     * 팀명으로 검색 (부분 일치)
     * @param teamName 팀명 (예: "삼성", "라이온즈")
     * @return 매칭되는 팀 목록
     */
    List<TeamEntity> findByTeamNameContaining(String teamName);

    /**
     * 연고지 도시로 조회
     * @param city 도시명 (예: "대구", "서울")
     * @return 해당 도시의 팀 목록
     */
    List<TeamEntity> findByCity(String city);

    /**
     * 창단 연도 범위로 조회
     * @param startYear 시작 연도
     * @param endYear 종료 연도
     * @return 해당 기간에 창단된 팀
     */
    List<TeamEntity> findByFoundedYearBetween(Integer startYear, Integer endYear);
}
