package com.example.mate.controller;

import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.service.CheckInRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInRecordController {

    private final CheckInRecordService checkInRecordService;

    // 체크인
    @PostMapping
    public ResponseEntity<?> checkIn(
            @RequestBody CheckInRecordDTO.Request request,
            java.security.Principal principal) {
        try {
            CheckInRecordDTO.Response response = checkInRecordService.checkIn(request, principal);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 파티별 체크인 기록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<java.util.List<CheckInRecordDTO.Response>> getCheckInsByPartyId(@PathVariable Long partyId) {
        java.util.List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByPartyId(partyId);
        return ResponseEntity.ok(records);
    }

    // 사용자별 체크인 기록 조회
    @GetMapping("/user/{userId}")
    public ResponseEntity<java.util.List<CheckInRecordDTO.Response>> getCheckInsByUserId(@PathVariable Long userId) {
        java.util.List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByUserId(userId);
        return ResponseEntity.ok(records);
    }

    // 파티별 체크인 인원 수 조회
    @GetMapping("/party/{partyId}/count")
    public ResponseEntity<Long> getCheckInCount(@PathVariable Long partyId) {
        long count = checkInRecordService.getCheckInCount(partyId);
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