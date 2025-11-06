package com.example.mate.controller;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.service.PartyApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class PartyApplicationController {

    private final PartyApplicationService applicationService;

    // 신청 생성
    @PostMapping
    public ResponseEntity<PartyApplicationDTO.Response> createApplication(
            @RequestBody PartyApplicationDTO.Request request) {
        try {
            PartyApplicationDTO.Response response = applicationService.createApplication(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // 파티별 신청 목록 조회
    @GetMapping("/party/{partyId}")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getApplicationsByPartyId(
            @PathVariable Long partyId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getApplicationsByPartyId(partyId);
        return ResponseEntity.ok(applications);
    }

    // 신청자별 신청 목록 조회
    @GetMapping("/applicant/{applicantId}")
    public ResponseEntity<List<PartyApplicationDTO.Response>> getApplicationsByApplicantId(
            @PathVariable Long applicantId) {
        List<PartyApplicationDTO.Response> applications = applicationService.getApplicationsByApplicantId(applicantId);
        return ResponseEntity.ok(applications);
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
    public ResponseEntity<PartyApplicationDTO.Response> approveApplication(
            @PathVariable Long applicationId) {
        try {
            PartyApplicationDTO.Response response = applicationService.approveApplication(applicationId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // 신청 거절
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<PartyApplicationDTO.Response> rejectApplication(
            @PathVariable Long applicationId) {
        try {
            PartyApplicationDTO.Response response = applicationService.rejectApplication(applicationId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // 신청 취소
    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> cancelApplication(
            @PathVariable Long applicationId,
            @RequestParam Long applicantId) {
        try {
            applicationService.cancelApplication(applicationId, applicantId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}