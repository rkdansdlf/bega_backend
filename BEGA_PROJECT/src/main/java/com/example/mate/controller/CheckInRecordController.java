package com.example.mate.controller;

import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.service.CheckInRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInRecordController {

    private final CheckInRecordService checkInRecordService;

    // 체크인
    @PostMapping
    public ResponseEntity<?> checkIn(
            @Valid @RequestBody CheckInRecordDTO.Request request,
            java.security.Principal principal) {
        CheckInRecordDTO.Response response = checkInRecordService.checkIn(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 체크인 QR 세션 발급
    @PostMapping("/qr-session")
    public ResponseEntity<?> createQrSession(
            @Valid @RequestBody CheckInRecordDTO.QrSessionRequest request,
            java.security.Principal principal) {
        CheckInRecordDTO.QrSessionResponse response = checkInRecordService.createQrSession(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파티별 체크인 기록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<java.util.List<CheckInRecordDTO.Response>> getCheckInsByPartyId(
            @PathVariable Long partyId,
            java.security.Principal principal) {
        java.util.List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByPartyId(partyId,
                principal);
        return ResponseEntity.ok(records);
    }

    // 사용자별 체크인 기록 조회
    @GetMapping("/user/{userId}")
    public ResponseEntity<java.util.List<CheckInRecordDTO.Response>> getCheckInsByUserId(
            @PathVariable Long userId,
            java.security.Principal principal) {
        java.util.List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByUserId(userId, principal);
        return ResponseEntity.ok(records);
    }

    // 파티별 체크인 인원 수 조회
    @GetMapping("/party/{partyId}/count")
    public ResponseEntity<Long> getCheckInCount(
            @PathVariable Long partyId,
            java.security.Principal principal) {
        long count = checkInRecordService.getCheckInCount(partyId, principal);
        return ResponseEntity.ok(count);
    }

    // 체크인 여부 확인
    @GetMapping("/check")
    public ResponseEntity<Boolean> isCheckedIn(
            @RequestParam Long partyId,
            java.security.Principal principal) {
        boolean isCheckedIn = checkInRecordService.isCheckedIn(partyId, principal);
        return ResponseEntity.ok(isCheckedIn);
    }
}
