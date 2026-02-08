package com.example.mate.repository;

import com.example.mate.entity.Party;
import com.example.mate.entity.Party.PartyStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {

        // 상태별 파티 조회
        List<Party> findByStatus(Party.PartyStatus status);

        // 호스트별 파티 조회
        List<Party> findByHostId(Long hostId);

        // 경기 날짜별 파티 조회
        List<Party> findByGameDate(LocalDate gameDate);

        // 구장별 파티 조회
        List<Party> findByStadium(String stadium);

        // 팀별 파티 조회
        List<Party> findByTeamId(String teamId);

        // 검색 (구장, 팀, 섹션, 호스트명)
        @Query("SELECT p FROM Party p WHERE " +
                        "LOWER(p.stadium) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.homeTeam) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.awayTeam) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.section) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.hostName) LIKE LOWER(CONCAT('%', :query, '%'))")
        List<Party> searchParties(@Param("query") String query);

        // 최신순 정렬
        List<Party> findAllByOrderByCreatedAtDesc();

        // 상태별 최신순 정렬
        List<Party> findByStatusOrderByCreatedAtDesc(Party.PartyStatus status);

        // 경기 날짜 이후 파티 조회
        List<Party> findByGameDateAfterOrderByGameDateAsc(LocalDate date);

        // 통합 검색 및 필터링 (팀, 구장, 날짜, 검색어, 상태 제외)
        @Query("SELECT p FROM Party p WHERE " +
                        "(:teamId IS NULL OR p.teamId = :teamId) AND " +
                        "(:stadium IS NULL OR p.stadium = :stadium) AND " +
                        "(:gameDate IS NULL OR p.gameDate = :gameDate) AND " +
                        "(:query IS NULL OR " +
                        "LOWER(p.stadium) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.homeTeam) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.awayTeam) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.section) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.hostName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
                        "(p.status NOT IN :excludedStatuses)")
        Page<Party> findPartiesWithFilter(
                        @Param("teamId") String teamId,
                        @Param("stadium") String stadium,
                        @Param("gameDate") LocalDate gameDate,
                        @Param("query") String query,
                        @Param("excludedStatuses") List<Party.PartyStatus> excludedStatuses,
                        Pageable pageable);

        Page<Party> findByStatusNotInOrderByCreatedAtDesc(List<PartyStatus> statuses, Pageable pageable);

        Page<Party> findByTeamIdAndStatusNotInOrderByCreatedAtDesc(String teamId, List<PartyStatus> statuses,
                        Pageable pageable);

        Page<Party> findByStadiumAndStatusNotInOrderByCreatedAtDesc(String stadium, List<PartyStatus> statuses,
                        Pageable pageable);

        Page<Party> findByTeamIdAndStadiumAndStatusNotInOrderByCreatedAtDesc(String teamId, String stadium,
                        List<PartyStatus> statuses, Pageable pageable);

        Page<Party> findAllByOrderByCreatedAtDesc(Pageable pageable);

        // 스케줄러용 쿼리 메서드들
        List<Party> findByStatusAndGameDateBefore(PartyStatus status, LocalDate date);

        List<Party> findByStatusAndGameDate(PartyStatus status, LocalDate date);

        // 사용자 삭제 시 cascade cleanup용 쿼리
        List<Party> findByHostIdAndStatusIn(Long hostId, List<PartyStatus> statuses);

}