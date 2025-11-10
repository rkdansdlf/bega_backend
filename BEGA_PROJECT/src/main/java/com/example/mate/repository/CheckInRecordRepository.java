package com.example.mate.repository;

import com.example.mate.entity.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {

    // 파티별 체크인 기록 조회
    List<CheckInRecord> findByPartyId(Long partyId);

    // 사용자별 체크인 기록 조회
    List<CheckInRecord> findByUserId(Long userId);

    // 특정 파티의 특정 사용자 체크인 확인
    Optional<CheckInRecord> findByPartyIdAndUserId(Long partyId, Long userId);

    // 파티별 체크인 인원 수
    long countByPartyId(Long partyId);
}