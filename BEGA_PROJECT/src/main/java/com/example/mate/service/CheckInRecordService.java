package com.example.mate.service;

import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.entity.CheckInRecord;
import com.example.mate.entity.Party;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckInRecordService {

    private final CheckInRecordRepository checkInRecordRepository;
    private final PartyRepository partyRepository;

    // 체크인
    @Transactional
    public CheckInRecordDTO.Response checkIn(CheckInRecordDTO.Request request) {
        // 중복 체크인 확인
        checkInRecordRepository.findByPartyIdAndUserId(request.getPartyId(), request.getUserId())
                .ifPresent(record -> {
                    throw new RuntimeException("이미 체크인하셨습니다.");
                });

        CheckInRecord record = CheckInRecord.builder()
                .partyId(request.getPartyId())
                .userId(request.getUserId())
                .location(request.getLocation())
                .build();

        CheckInRecord savedRecord = checkInRecordRepository.save(record);

        // 모든 참여자가 체크인했는지 확인
        checkAndUpdatePartyStatus(request.getPartyId());

        return CheckInRecordDTO.Response.from(savedRecord);
    }

    // 파티별 체크인 기록 조회
    @Transactional(readOnly = true)
    public List<CheckInRecordDTO.Response> getCheckInsByPartyId(Long partyId) {
        return checkInRecordRepository.findByPartyId(partyId).stream()
                .map(CheckInRecordDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 사용자별 체크인 기록 조회
    @Transactional(readOnly = true)
    public List<CheckInRecordDTO.Response> getCheckInsByUserId(Long userId) {
        return checkInRecordRepository.findByUserId(userId).stream()
                .map(CheckInRecordDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 체크인 여부 확인
    @Transactional(readOnly = true)
    public boolean isCheckedIn(Long partyId, Long userId) {
        return checkInRecordRepository.findByPartyIdAndUserId(partyId, userId).isPresent();
    }

    // 파티별 체크인 인원 수 조회
    @Transactional(readOnly = true)
    public long getCheckInCount(Long partyId) {
        return checkInRecordRepository.countByPartyId(partyId);
    }

    // 모든 참여자가 체크인했는지 확인하고 파티 상태 업데이트
    @Transactional
    public void checkAndUpdatePartyStatus(Long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new RuntimeException("파티를 찾을 수 없습니다."));

        long checkInCount = checkInRecordRepository.countByPartyId(partyId);

        // 모든 참여자가 체크인한 경우
        if (checkInCount == party.getCurrentParticipants()) {
            party.setStatus(Party.PartyStatus.CHECKED_IN);
            partyRepository.save(party);
        }
    }
}