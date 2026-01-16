package com.example.mate.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.mate.entity.PartyApplication;

@Repository
public interface PartyApplicationRepository extends JpaRepository<PartyApplication, Long> {

    // 파티별 신청 목록
    List<PartyApplication> findByPartyId(Long partyId);

    // 신청자별 신청 목록
    List<PartyApplication> findByApplicantId(Long applicantId);

    // 파티별 승인된 신청 목록
    List<PartyApplication> findByPartyIdAndIsApprovedTrue(Long partyId);

    // 파티별 대기중인 신청 목록
    List<PartyApplication> findByPartyIdAndIsApprovedFalseAndIsRejectedFalse(Long partyId);

    // 파티별 거절된 신청 목록
    List<PartyApplication> findByPartyIdAndIsRejectedTrue(Long partyId);

    // 특정 신청자의 특정 파티 신청 확인
    Optional<PartyApplication> findByPartyIdAndApplicantId(Long partyId, Long applicantId);

    // 신청자의 승인된 신청 목록
    List<PartyApplication> findByApplicantIdAndIsApprovedTrue(Long applicantId);

    void deleteByPartyId(Long partyId);

    // 통계
    @Query("SELECT COUNT(DISTINCT p.id) FROM Party p " +
            "WHERE p.status = com.example.mate.entity.Party$PartyStatus.CHECKED_IN AND " +
            "(p.hostId = :userId OR " +
            "EXISTS (SELECT 1 FROM PartyApplication pa " +
            "        WHERE pa.partyId = p.id " +
            "        AND pa.applicantId = :userId " +
            "        AND pa.isApproved = true))")
    int countCheckedInPartiesByUserId(@Param("userId") Long userId);

}