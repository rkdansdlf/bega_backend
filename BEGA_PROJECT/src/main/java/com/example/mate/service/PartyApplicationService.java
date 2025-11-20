package com.example.mate.service;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.exception.DuplicateApplicationException;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyApplicationNotFoundException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartyApplicationService {

    private final PartyApplicationRepository applicationRepository;
    private final PartyRepository partyRepository;
    private final PartyService partyService;

    // 신청 생성
    @Transactional
    public PartyApplicationDTO.Response createApplication(PartyApplicationDTO.Request request) {
        // 중복 신청 체크
        applicationRepository.findByPartyIdAndApplicantId(request.getPartyId(), request.getApplicantId())
                .ifPresent(app -> {
                    throw new DuplicateApplicationException(request.getPartyId(), request.getApplicantId());
                });

        // 파티 존재 여부 확인
        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(request.getPartyId()));
        // 파티가 가득 찼는지 확인
        if (party.getCurrentParticipants() >= party.getMaxParticipants()) {
            throw new PartyFullException(request.getPartyId());
        }

        PartyApplication application = PartyApplication.builder()
                .partyId(request.getPartyId())
                .applicantId(request.getApplicantId())
                .applicantName(request.getApplicantName())
                .applicantBadge(request.getApplicantBadge() != null ? request.getApplicantBadge() : Party.BadgeType.NEW)
                .applicantRating(request.getApplicantRating() != null ? request.getApplicantRating() : 5.0)
                .message(request.getMessage())
                .depositAmount(request.getDepositAmount())
                .paymentType(request.getPaymentType())
                .isPaid(false)
                .isApproved(false)
                .isRejected(false)
                .build();

        // 전액 결제인 경우 자동 승인
        if (request.getPaymentType() == PartyApplication.PaymentType.FULL) {
            application.setIsApproved(true);
            application.setApprovedAt(LocalDateTime.now());
            partyService.incrementParticipants(request.getPartyId());
        }

        PartyApplication savedApplication = applicationRepository.save(application);
        return PartyApplicationDTO.Response.from(savedApplication);
    }

    // 파티별 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApplicationsByPartyId(Long partyId) {
        return applicationRepository.findByPartyId(partyId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 신청자별 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApplicationsByApplicantId(Long applicantId) {
        return applicationRepository.findByApplicantId(applicantId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 대기중인 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getPendingApplications(Long partyId) {
        return applicationRepository.findByPartyIdAndIsApprovedFalseAndIsRejectedFalse(partyId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 승인된 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApprovedApplications(Long partyId) {
        return applicationRepository.findByPartyIdAndIsApprovedTrue(partyId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 거절된 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getRejectedApplications(Long partyId) {
        return applicationRepository.findByPartyIdAndIsRejectedTrue(partyId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 신청 승인
    @Transactional
    public PartyApplicationDTO.Response approveApplication(Long applicationId) {
        PartyApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new PartyApplicationNotFoundException(applicationId));

        if (application.getIsApproved()) {
            throw new InvalidApplicationStatusException("이미 승인된 신청입니다.");
        }

        if (application.getIsRejected()) {
            throw new InvalidApplicationStatusException("거절된 신청은 승인할 수 없습니다.");
        }

        application.setIsApproved(true);
        application.setApprovedAt(LocalDateTime.now());

        // 파티 참여 인원 증가
        partyService.incrementParticipants(application.getPartyId());

        PartyApplication savedApplication = applicationRepository.save(application);
        return PartyApplicationDTO.Response.from(savedApplication);
    }

    // 신청 거절
    @Transactional
    public PartyApplicationDTO.Response rejectApplication(Long applicationId) {
        PartyApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new PartyApplicationNotFoundException(applicationId));

        if (application.getIsApproved()) {
            throw new InvalidApplicationStatusException("승인된 신청은 거절할 수 없습니다.");
        }

        if (application.getIsRejected()) {
            throw new InvalidApplicationStatusException("이미 거절된 신청입니다.");
        }

        application.setIsRejected(true);
        application.setRejectedAt(LocalDateTime.now());

        PartyApplication savedApplication = applicationRepository.save(application);
        return PartyApplicationDTO.Response.from(savedApplication);
    }

    // 신청 취소 (신청자가 취소)
    @Transactional
    public void cancelApplication(Long applicationId, Long applicantId) {
        PartyApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new PartyApplicationNotFoundException(applicationId));

        if (!application.getApplicantId().equals(applicantId)) {
            throw new UnauthorizedAccessException("본인의 신청만 취소할 수 있습니다.");
        }

        if (application.getIsApproved()) {
            // 승인된 신청 취소 시 참여 인원 감소
            partyService.decrementParticipants(application.getPartyId());
        }

        applicationRepository.delete(application);
    }
}