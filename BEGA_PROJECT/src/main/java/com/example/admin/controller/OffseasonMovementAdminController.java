package com.example.admin.controller;

import com.example.admin.dto.OffseasonMovementAdminRequest;
import com.example.admin.service.OffseasonMovementAdminService;
import com.example.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/admin/offseason/movements", produces = "application/json; charset=UTF-8")
@RequiredArgsConstructor
@Slf4j
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class OffseasonMovementAdminController {

    private final OffseasonMovementAdminService offseasonMovementAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse> getMovements(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String teamCode,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.success(
                "스토브리그 이동 목록 조회 성공",
                offseasonMovementAdminService.getMovements(search, section, teamCode, fromDate, toDate)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createMovement(
            @Valid @RequestBody OffseasonMovementAdminRequest request) {
        log.info("스토브리그 이동 등록 요청: playerName={}, teamCode={}", request.getPlayerName(), request.getTeamCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "스토브리그 이동이 등록되었습니다.",
                        offseasonMovementAdminService.createMovement(request)));
    }

    @PutMapping("/{movementId}")
    public ResponseEntity<ApiResponse> updateMovement(
            @PathVariable Long movementId,
            @Valid @RequestBody OffseasonMovementAdminRequest request) {
        log.info("스토브리그 이동 수정 요청: movementId={}, playerName={}", movementId, request.getPlayerName());
        return ResponseEntity.ok(ApiResponse.success(
                "스토브리그 이동이 수정되었습니다.",
                offseasonMovementAdminService.updateMovement(movementId, request)));
    }

    @DeleteMapping("/{movementId}")
    public ResponseEntity<ApiResponse> deleteMovement(@PathVariable Long movementId) {
        log.info("스토브리그 이동 삭제 요청: movementId={}", movementId);
        offseasonMovementAdminService.deleteMovement(movementId);
        return ResponseEntity.ok(ApiResponse.success(
                "스토브리그 이동이 삭제되었습니다.",
                Map.of("movementId", movementId)));
    }
}
