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
import java.util.Optional;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long>, PartyRepositoryCustom {

        // 상태별 파티 조회
        List<Party> findByStatus(Party.PartyStatus status);

        // 호스트별 파티 조회
        List<Party> findByHostId(Long hostId);

        Optional<Party> findByIdAndHostId(Long id, Long hostId);

        @Query("""
                        SELECT p
                        FROM Party p
                        WHERE p.id = :partyId
                          AND (
                                p.hostId = :userId
                                OR EXISTS (
                                    SELECT 1
                                    FROM PartyApplication a
                                    WHERE a.partyId = p.id
                                      AND a.applicantId = :userId
                                      AND a.isApproved = true
                                )
                          )
                        """)
        Optional<Party> findAccessibleByIdAndParticipantId(
                        @Param("partyId") Long partyId,
                        @Param("userId") Long userId);

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

        Page<Party> findByStatusAndGameDateGreaterThanEqual(PartyStatus status, LocalDate gameDate, Pageable pageable);

        // 스케줄러용 쿼리 메서드들
        List<Party> findByStatusAndGameDateBefore(PartyStatus status, LocalDate date);

        List<Party> findByStatusAndGameDate(PartyStatus status, LocalDate date);

        // 사용자 삭제 시 cascade cleanup용 쿼리
        List<Party> findByHostIdAndStatusIn(Long hostId, List<PartyStatus> statuses);

        long countByHostIdAndStatusIn(Long hostId, List<PartyStatus> statuses);

        @Query("""
                        SELECT COUNT(p.id)
                        FROM Party p
                        WHERE p.hostId = :hostId
                          AND p.status IN :statuses
                          AND p.gameDate >= :cutoffDate
                          AND NOT EXISTS (
                              SELECT 1
                              FROM CheckInRecord c
                              WHERE c.partyId = p.id
                                AND c.userId = :hostId
                          )
                        """)
        long countHostedNoShowsSince(
                        @Param("hostId") Long hostId,
                        @Param("statuses") List<PartyStatus> statuses,
                        @Param("cutoffDate") LocalDate cutoffDate);

}
