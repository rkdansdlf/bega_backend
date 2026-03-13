package com.example.mate.controller;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.service.PartyApplicationService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class PartyApplicationController {

    private final PartyApplicationService applicationService;

    // 신청 생성 — applicantId는 인증 principal에서 파생
    @PostMapping
    public ResponseEntity<?> createApplication(
            @RequestBody PartyApplicationDTO.Request request,
            Principal principal) {
        PartyApplicationDTO.Response response = applicationService.createApplication(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 파티별 신청 목록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<?> getApplicationsByPartyId(
            @PathVariable Long partyId,
            Principal principal) {
        List<PartyApplicationDTO.Response> applications = applicationService.getApplicationsByPartyId(partyId, principal);
        return ResponseEntity.ok(applications);
    }

    // 특정 파티에 대한 내 신청 단건 조회
    @GetMapping("/party/{partyId}/mine")
    public ResponseEntity<?> getMyApplicationByPartyId(
            @PathVariable Long partyId,
            Principal principal) {
        PartyApplicationDTO.Response application = applicationService.getMyApplicationByPartyId(partyId, principal);
        return ResponseEntity.ok(application);
    }

    // 내 신청 목록 조회 (로그인 사용자 기준)
    @GetMapping("/my")
    public ResponseEntity<?> getMyApplications(Principal principal) {
        List<PartyApplicationDTO.Response> applications = applicationService.getMyApplications(principal);
        return ResponseEntity.ok(applications);
    }

    // 대기중인 신청 목록 조회 (호스트 전용)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/party/{partyId}/pending")
    public ResponseEntity<?> getPendingApplications(
            @PathVariable Long partyId,
            Principal principal) {
        List<PartyApplicationDTO.Response> applications = applicationService.getPendingApplications(partyId, principal);
        return ResponseEntity.ok(applications);
    }

    // 승인된 신청 목록 조회 (호스트 전용)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/party/{partyId}/approved")
    public ResponseEntity<?> getApprovedApplications(
            @PathVariable Long partyId,
            Principal principal) {
        List<PartyApplicationDTO.Response> applications = applicationService.getApprovedApplications(partyId, principal);
        return ResponseEntity.ok(applications);
    }

    // 거절된 신청 목록 조회 (호스트 전용)
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/party/{partyId}/rejected")
    public ResponseEntity<?> getRejectedApplications(
            @PathVariable Long partyId,
            Principal principal) {
        List<PartyApplicationDTO.Response> applications = applicationService.getRejectedApplications(partyId, principal);
        return ResponseEntity.ok(applications);
    }

    // 신청 승인
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<?> approveApplication(
            @PathVariable Long applicationId,
            Principal principal) {
        PartyApplicationDTO.Response response = applicationService.approveApplication(applicationId, principal);
        return ResponseEntity.ok(response);
    }

    // 신청 거절
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<?> rejectApplication(
            @PathVariable Long applicationId,
            Principal principal) {
        PartyApplicationDTO.Response response = applicationService.rejectApplication(applicationId, principal);
        return ResponseEntity.ok(response);
    }

    // 신청 취소 — applicantId는 인증 principal에서 파생
    @PostMapping("/{applicationId}/cancel")
    public ResponseEntity<?> cancelApplicationWithReason(
            @PathVariable Long applicationId,
            @RequestBody(required = false) PartyApplicationDTO.CancelRequest request,
            Principal principal) {
        PartyApplicationDTO.CancelRequest cancelRequest = request != null
                ? request
                : PartyApplicationDTO.CancelRequest.builder().build();
        PartyApplicationDTO.CancelResponse response = applicationService.cancelApplication(
                applicationId,
                principal,
                cancelRequest);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{applicationId}")
    public ResponseEntity<?> cancelApplication(
            @PathVariable Long applicationId,
            Principal principal) {
        applicationService.cancelApplication(applicationId, principal);
        return ResponseEntity.noContent().build();
    }
}
