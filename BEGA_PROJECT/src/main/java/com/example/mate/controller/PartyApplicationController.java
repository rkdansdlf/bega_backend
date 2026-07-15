package com.example.mate.controller;

import com.example.common.web.AuthenticatedUserIds;
import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.service.PartyApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class PartyApplicationController {

    private final PartyApplicationService applicationService;

    // 신청 생성 — applicantId는 인증 principal에서 파생
    @PostMapping
    public ResponseEntity<PartyApplicationDTO.Response> createApplication(
            @Valid @RequestBody PartyApplicationDTO.Request request,
            @AuthenticationPrincipal Long userId) {
        Long authenticatedUserId = AuthenticatedUserIds.require(userId);
        PartyApplicationDTO.Response response = applicationService.createApplication(request, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파티별 신청 목록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getApplicationsByPartyId(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getApplicationsByPartyId(
                partyId,
                AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(applications);
    }

    // 특정 파티에 대한 내 신청 단건 조회
    @GetMapping("/party/{partyId}/mine")
    public ResponseEntity<PartyApplicationDTO.Response> getMyApplicationByPartyId(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        PartyApplicationDTO.Response application = applicationService.getMyApplicationByPartyId(
                partyId,
                AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(application);
    }

    // 내 신청 목록 조회 (로그인 사용자 기준)
    @GetMapping("/my")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getMyApplications(@AuthenticationPrincipal Long userId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getMyApplications(AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(applications);
    }

    // 대기중인 신청 목록 조회 (호스트 전용)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/party/{partyId}/pending")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getPendingApplications(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getPendingApplications(
                partyId,
                AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(applications);
    }

    // 승인된 신청 목록 조회 (호스트 전용)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/party/{partyId}/approved")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getApprovedApplications(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getApprovedApplications(
                partyId,
                AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(applications);
    }

    // 거절된 신청 목록 조회 (호스트 전용)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/party/{partyId}/rejected")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getRejectedApplications(
            @PathVariable Long partyId,
            @AuthenticationPrincipal Long userId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getRejectedApplications(
                partyId,
                AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(applications);
    }

    // 신청 승인
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<PartyApplicationDTO.Response> approveApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal Long userId) {
        PartyApplicationDTO.Response response = applicationService.approveApplication(applicationId, AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(response);
    }

    // 신청 거절
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<PartyApplicationDTO.Response> rejectApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal Long userId) {
        PartyApplicationDTO.Response response = applicationService.rejectApplication(applicationId, AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(response);
    }

    // 신청 취소 — applicantId는 인증 principal에서 파생
    @PostMapping("/{applicationId}/cancel")
    public ResponseEntity<PartyApplicationDTO.CancelResponse> cancelApplicationWithReason(
            @PathVariable Long applicationId,
            @Valid @RequestBody(required = false) PartyApplicationDTO.CancelRequest request,
            @AuthenticationPrincipal Long userId) {
        PartyApplicationDTO.CancelRequest cancelRequest = request != null
                ? request
                : PartyApplicationDTO.CancelRequest.builder().build();
        PartyApplicationDTO.CancelResponse response = applicationService.cancelApplication(
                applicationId,
                AuthenticatedUserIds.require(userId),
                cancelRequest);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> cancelApplication(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal Long userId) {
        applicationService.cancelApplication(applicationId, AuthenticatedUserIds.require(userId));
        return ResponseEntity.noContent().build();
    }
}
