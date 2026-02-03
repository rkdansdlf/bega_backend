package com.example.kbo.repository;

import com.example.kbo.entity.TeamEntity;
import com.example.kbo.entity.TeamFranchiseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 팀 리포지토리
 *
 * <p>팀 데이터에 접근하기 위한 리포지토리입니다.</p>
 */
@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, String> {

    /**
     * 팀 ID로 팀 조회
     *
     * @param teamId 팀 ID
     * @return 팀 엔티티
     */
    Optional<TeamEntity> findByTeamId(String teamId);

    /**
     * 팀 이름으로 팀 조회
     *
     * @param teamName 팀 이름
     * @return 팀 엔티티
     */
    Optional<TeamEntity> findByTeamName(String teamName);

    /**
     * 팀 ID와 활성 여부로 팀 조회
     *
     * @param teamId 팀 ID
     * @param isActive 활성 여부
     * @return 팀 엔티티
     */
    Optional<TeamEntity> findByTeamIdAndIsActive(String teamId, Boolean isActive);

    /**
     * 활성 팀 목록 조회
     *
     * @param isActive 활성 여부
     * @return 팀 엔티티 목록
     */
    List<TeamEntity> findByIsActive(Boolean isActive);

    /**
     * 활성화된 팀만 조회
     *
     * @return 활성 팀 목록
     */
    @Query("SELECT t FROM TeamEntity t WHERE t.isActive = true")
    List<TeamEntity> findAllActiveTeams();

    /**
     * 프랜차이즈로 팀 조회
     *
     * @param franchise 프랜차이즈 엔티티
     * @return 팀 엔티티 목록
     */
    List<TeamEntity> findByFranchise(TeamFranchiseEntity franchise);

    /**
     * 프랜차이즈 ID로 팀 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @return 팀 엔티티 목록
     */
    @Query("SELECT t FROM TeamEntity t WHERE t.franchise.id = :franchiseId")
    List<TeamEntity> findByFranchiseId(@Param("franchiseId") Integer franchiseId);

    /**
     * 프랜차이즈 ID와 활성 여부로 팀 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @param isActive 활성 여부
     * @return 팀 엔티티
     */
    @Query("SELECT t FROM TeamEntity t WHERE t.franchise.id = :franchiseId AND t.isActive = :isActive")
    Optional<TeamEntity> findByFranchiseIdAndIsActive(
        @Param("franchiseId") Integer franchiseId,
        @Param("isActive") Boolean isActive
    );

    /**
     * 도시로 팀 목록 조회
     *
     * @param city 도시 이름
     * @return 팀 엔티티 목록
     */
    List<TeamEntity> findByCity(String city);

    /**
     * 팀 이름에 특정 키워드가 포함된 팀 조회
     *
     * @param keyword 검색 키워드
     * @return 팀 엔티티 목록
     */
    @Query("SELECT t FROM TeamEntity t WHERE t.teamName LIKE %:keyword% OR t.teamShortName LIKE %:keyword%")
    List<TeamEntity> findByTeamNameContaining(@Param("keyword") String keyword);
}
