package com.example.mate.controller;

import com.example.common.exception.BadRequestBusinessException;
import com.example.common.web.AuthenticatedUserIds;
import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.service.CheckInRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInRecordController {

    private static final int MAX_CHECK_IN_HISTORY_PAGE_SIZE = 100;

    private final CheckInRecordService checkInRecordService;

    // 체크인
    @PostMapping
    public ResponseEntity<CheckInRecordDTO.Response> checkIn(
            @Valid @RequestBody CheckInRecordDTO.Request request,
            @AuthenticationPrincipal Long userId) {
        CheckInRecordDTO.Response response = checkInRecordService.checkIn(request, AuthenticatedUserIds.require(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 체크인 QR 세션 발급
    @PostMapping("/qr-session")
    public ResponseEntity<CheckInRecordDTO.QrSessionResponse> createQrSession(
            @Valid @RequestBody CheckInRecordDTO.QrSessionRequest request,
            @AuthenticationPrincipal Long userId) {
        CheckInRecordDTO.QrSessionResponse response = checkInRecordService.createQrSession(request, AuthenticatedUserIds.require(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파티별 체크인 기록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<CheckInRecordDTO.Response>> getCheckInsByPartyId(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        List<CheckInRecordDTO.Response> records = checkInRecordService.getCheckInsByPartyId(
                partyId,
                AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(records);
    }

    // 사용자별 체크인 기록 조회
    @GetMapping("/user/{userId}")
    @Operation(summary = "사용자별 체크인 기록을 페이지 단위로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "체크인 기록 페이지",
                    content = @Content(schema = @Schema(implementation = CheckInRecordDTO.HistoryPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "page 또는 size가 허용 범위를 벗어났습니다.",
                    content = @Content(schema = @Schema(implementation = com.example.common.dto.ApiResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인의 체크인 기록만 조회할 수 있습니다.",
                    content = @Content(schema = @Schema(implementation = com.example.common.dto.ApiResponse.class)))
    })
    public ResponseEntity<CheckInRecordDTO.HistoryPageResponse> getCheckInsByUserId(
            @PathVariable Long userId,
            @AuthenticationPrincipal Long requesterUserId,
            @RequestParam(defaultValue = "0")
            @Parameter(description = "0부터 시작하는 페이지 번호 (page × size는 2147483647 이하)", example = "0",
                    schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0")) int page,
            @RequestParam(defaultValue = "30")
            @Parameter(description = "페이지 크기(1~100)", example = "30",
                    schema = @Schema(type = "integer", format = "int32", defaultValue = "30",
                            minimum = "1", maximum = "100")) int size) {
        if (page < 0) {
            throw new BadRequestBusinessException("CHECKIN_PAGE_INVALID", "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_CHECK_IN_HISTORY_PAGE_SIZE) {
            throw new BadRequestBusinessException("CHECKIN_PAGE_SIZE_INVALID", "페이지 크기는 1~100건이어야 합니다.");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BadRequestBusinessException("CHECKIN_PAGE_INVALID", "페이지 번호가 허용 범위를 벗어났습니다.");
        }

        CheckInRecordDTO.HistoryPageResponse records = checkInRecordService.getCheckInsByUserId(
                userId,
                AuthenticatedUserIds.require(requesterUserId),
                PageRequest.of(page, size));
        return ResponseEntity.ok(records);
    }

    // 파티별 체크인 인원 수 조회
    @GetMapping("/party/{partyId}/count")
    public ResponseEntity<Long> getCheckInCount(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        long count = checkInRecordService.getCheckInCount(partyId, AuthenticatedUserIds.require(userId));
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
}
