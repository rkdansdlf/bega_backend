package com.example.mate.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.Party;

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

    Optional<PartyApplication> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pa from PartyApplication pa where pa.orderId = :orderId")
    Optional<PartyApplication> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pa from PartyApplication pa where pa.paymentKey = :paymentKey")
    Optional<PartyApplication> findByPaymentKeyForUpdate(@Param("paymentKey") String paymentKey);

    Optional<PartyApplication> findByPaymentKey(String paymentKey);

    long countByOrderId(String orderId);

    // 신청자의 승인된 신청 목록
    List<PartyApplication> findByApplicantIdAndIsApprovedTrue(Long applicantId);

    @Query("SELECT pa FROM PartyApplication pa WHERE pa.applicantId = :applicantId AND pa.isApproved = true "
            + "AND EXISTS (SELECT 1 FROM Party p WHERE p.id = pa.partyId AND p.status IN :statuses)")
    List<PartyApplication> findApprovedByApplicantIdAndPartyStatusIn(@Param("applicantId") Long applicantId,
            @Param("statuses") List<Party.PartyStatus> statuses);

    // 파티별 승인된 신청 수
    long countByPartyIdAndIsApprovedTrue(Long partyId);

    // 신청자별 승인된 신청 수 (TRUSTED 배지 판단용)
    long countByApplicantIdAndIsApprovedTrue(Long applicantId);

    // 파티별 대기 중인 신청 수
    long countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(Long partyId);

    // 특정 파티에 대한 거절된 신청 존재 여부 확인 (재신청 차단용)
    boolean existsByPartyIdAndApplicantIdAndIsRejectedTrue(Long partyId, Long applicantId);

    void deleteByPartyId(Long partyId);

    // 응답 기한이 지난 미처리 신청 조회 (자동 거절용)
    List<PartyApplication> findByIsApprovedFalseAndIsRejectedFalseAndResponseDeadlineBefore(Instant deadline);

    // 통계
    @Query("SELECT COUNT(DISTINCT p.id) FROM Party p " +
            "WHERE p.status = com.example.mate.entity.Party$PartyStatus.CHECKED_IN AND " +
            "(p.hostId = :userId OR " +
            "EXISTS (SELECT 1 FROM PartyApplication pa " +
            "        WHERE pa.partyId = p.id " +
            "        AND pa.applicantId = :userId " +
            "        AND pa.isApproved = true))")
    int countCheckedInPartiesByUserId(@Param("userId") Long userId);

    // 응답 기한이 임박한 미처리 신청 조회 (넛지 알림용)
    List<PartyApplication> findByIsApprovedFalseAndIsRejectedFalseAndResponseDeadlineBetween(Instant from, Instant to);

    // 사용자 삭제 시 cascade cleanup용 쿼리
    List<PartyApplication> findByApplicantIdAndIsApprovedTrueAndIsRejectedFalse(Long applicantId);

}
