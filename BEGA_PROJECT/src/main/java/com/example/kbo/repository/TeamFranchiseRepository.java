package com.example.kbo.repository;

import com.example.kbo.entity.TeamFranchiseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 팀 프랜차이즈 리포지토리
 *
 * <p>프랜차이즈(구단) 데이터에 접근하기 위한 리포지토리입니다.</p>
 */
@Repository
public interface TeamFranchiseRepository extends JpaRepository<TeamFranchiseEntity, Integer> {

    /**
     * 원래 팀 코드로 프랜차이즈 조회
     *
     * @param originalCode 원래 팀 코드 (예: "HH")
     * @return 프랜차이즈 엔티티
     */
    Optional<TeamFranchiseEntity> findByOriginalCode(String originalCode);

    /**
     * 현재 팀 코드로 프랜차이즈 조회
     *
     * @param currentCode 현재 팀 코드 (예: "WO")
     * @return 프랜차이즈 엔티티
     */
    Optional<TeamFranchiseEntity> findByCurrentCode(String currentCode);

    /**
     * 프랜차이즈 이름으로 조회
     *
     * @param name 프랜차이즈 이름
     * @return 프랜차이즈 엔티티
     */
    Optional<TeamFranchiseEntity> findByName(String name);

    /**
     * 프랜차이즈 이름에 특정 키워드가 포함된 것 조회
     *
     * @param keyword 검색 키워드
     * @return 프랜차이즈 엔티티 목록
     */
    @Query("SELECT f FROM TeamFranchiseEntity f WHERE f.name LIKE %:keyword%")
    java.util.List<TeamFranchiseEntity> findByNameContaining(@Param("keyword") String keyword);
}
