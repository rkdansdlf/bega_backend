package com.example.mate.controller;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.service.PartyApplicationService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        try {
            PartyApplicationDTO.Response response = applicationService.createApplication(request, principal);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (com.example.common.exception.IdentityVerificationRequiredException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 파티별 신청 목록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<?> getApplicationsByPartyId(
            @PathVariable Long partyId,
            Principal principal) {
        try {
            List<PartyApplicationDTO.Response> applications = applicationService.getApplicationsByPartyId(partyId, principal);
            return ResponseEntity.ok(applications);
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 특정 파티에 대한 내 신청 단건 조회
    @GetMapping("/party/{partyId}/mine")
    public ResponseEntity<?> getMyApplicationByPartyId(
            @PathVariable Long partyId,
            Principal principal) {
        try {
            PartyApplicationDTO.Response application = applicationService.getMyApplicationByPartyId(partyId, principal);
            return ResponseEntity.ok(application);
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 내 신청 목록 조회 (로그인 사용자 기준)
    @GetMapping("/my")
    public ResponseEntity<?> getMyApplications(Principal principal) {
        try {
            List<PartyApplicationDTO.Response> applications = applicationService.getMyApplications(principal);
            return ResponseEntity.ok(applications);
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 대기중인 신청 목록 조회
    @GetMapping("/party/{partyId}/pending")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getPendingApplications(
            @PathVariable Long partyId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getPendingApplications(partyId);
        return ResponseEntity.ok(applications);
    }

    // 승인된 신청 목록 조회
    @GetMapping("/party/{partyId}/approved")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getApprovedApplications(
            @PathVariable Long partyId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getApprovedApplications(partyId);
        return ResponseEntity.ok(applications);
    }

    // 거절된 신청 목록 조회
    @GetMapping("/party/{partyId}/rejected")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getRejectedApplications(
            @PathVariable Long partyId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getRejectedApplications(partyId);
        return ResponseEntity.ok(applications);
    }

    // 신청 승인
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<?> approveApplication(
            @PathVariable Long applicationId,
            Principal principal) {
        try {
            PartyApplicationDTO.Response response = applicationService.approveApplication(applicationId, principal);
            return ResponseEntity.ok(response);
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 신청 거절
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<?> rejectApplication(
            @PathVariable Long applicationId,
            Principal principal) {
        try {
            PartyApplicationDTO.Response response = applicationService.rejectApplication(applicationId, principal);
            return ResponseEntity.ok(response);
        } catch (com.example.mate.exception.UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 신청 취소 — applicantId는 인증 principal에서 파생
    @PostMapping("/{applicationId}/cancel")
    public ResponseEntity<?> cancelApplicationWithReason(
            @PathVariable Long applicationId,
            @RequestBody(required = false) PartyApplicationDTO.CancelRequest request,
            Principal principal) {
        try {
            PartyApplicationDTO.CancelRequest cancelRequest = request != null
                    ? request
                    : PartyApplicationDTO.CancelRequest.builder().build();
            PartyApplicationDTO.CancelResponse response = applicationService.cancelApplication(
                    applicationId,
                    principal,
                    cancelRequest);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{applicationId}")
    public ResponseEntity<?> cancelApplication(
            @PathVariable Long applicationId,
            Principal principal) {
        try {
            applicationService.cancelApplication(applicationId, principal);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
