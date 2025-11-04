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
@CrossOrigin(origins = "*")
public class CheckInRecordController {

    private final CheckInRecordService checkInRecordService;

    // 체크인
    @PostMapping
    public ResponseEntity<CheckInRecordDTO.Response> checkIn(@RequestBody CheckInRecordDTO.Request request) {
        try {
            CheckInRecordDTO.Response response = checkInRecordService.checkIn(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // 파티별 체크인 기록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<CheckInRecordDTO.Response>> getCheckInsByPartyId(@PathVariable Long partyId) {
        List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByPartyId(partyId);
        return ResponseEntity.ok(records);
    }

    // 사용자별 체크인 기록 조회
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CheckInRecordDTO.Response>> getCheckInsByUserId(@PathVariable Long userId) {
        List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByUserId(userId);
        return ResponseEntity.ok(records);
    }

    // 체크인 여부 확인
    @GetMapping("/check")
    public ResponseEntity<Boolean> isCheckedIn(
            @RequestParam Long partyId,
            @RequestParam Long userId) {
        boolean isCheckedIn = checkInRecordService.isCheckedIn(partyId, userId);
        return ResponseEntity.ok(isCheckedIn);
    }

    // 파티별 체크인 인원 수 조회
    @GetMapping("/party/{partyId}/count")
    public ResponseEntity<Long> getCheckInCount(@PathVariable Long partyId) {
        long count = checkInRecordService.getCheckInCount(partyId);
        return ResponseEntity.ok(count);
    }
}