package com.example.mate.controller;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.service.CheckInRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInRecordController {

    private final CheckInRecordService checkInRecordService;

    // 체크인
    @PostMapping
    public ResponseEntity<CheckInRecordDTO.Response> checkIn(
            @Valid @RequestBody CheckInRecordDTO.Request request,
            @AuthenticationPrincipal Long userId) {
        CheckInRecordDTO.Response response = checkInRecordService.checkIn(request, requireUserId(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 체크인 QR 세션 발급
    @PostMapping("/qr-session")
    public ResponseEntity<CheckInRecordDTO.QrSessionResponse> createQrSession(
            @Valid @RequestBody CheckInRecordDTO.QrSessionRequest request,
            @AuthenticationPrincipal Long userId) {
        CheckInRecordDTO.QrSessionResponse response = checkInRecordService.createQrSession(request, requireUserId(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파티별 체크인 기록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<CheckInRecordDTO.Response>> getCheckInsByPartyId(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByPartyId(
                partyId,
                requireUserId(userId));
        return ResponseEntity.ok(records);
    }

    // 사용자별 체크인 기록 조회
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CheckInRecordDTO.Response>> getCheckInsByUserId(
            @PathVariable Long userId,
            @AuthenticationPrincipal Long requesterUserId) {
        List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByUserId(
                userId,
                requireUserId(requesterUserId));
        return ResponseEntity.ok(records);
    }

    // 파티별 체크인 인원 수 조회
    @GetMapping("/party/{partyId}/count")
    public ResponseEntity<Long> getCheckInCount(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        long count = checkInRecordService.getCheckInCount(partyId, requireUserId(userId));
        return ResponseEntity.ok(count);
    }

    // 체크인 여부 확인
    @GetMapping("/check")
    public ResponseEntity<Boolean> isCheckedIn(
            @RequestParam Long partyId,
            @AuthenticationPrincipal Long userId) {
        boolean isCheckedIn = checkInRecordService.isCheckedIn(partyId, userId);
        return ResponseEntity.ok(isCheckedIn);
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        return userId;
    }
}
