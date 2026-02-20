package com.example.mate.service;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.auth.service.UserService;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.kbo.util.TicketTeamNormalizer;
import com.example.mate.exception.DuplicateApplicationException;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyApplicationNotFoundException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.notification.service.NotificationService;
import com.example.notification.entity.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyApplicationService {

    private final PartyApplicationRepository applicationRepository;
    private final PartyRepository partyRepository;
    private final PartyService partyService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final TicketVerificationTokenStore ticketVerificationTokenStore;

    /**
     * 신청 생성 (Principal 기반 — 컨트롤러에서 호출)
     * applicantId는 JWT principal에서 파생하여 스푸핑 방지
     */
    @Transactional
    public PartyApplicationDTO.Response createApplication(PartyApplicationDTO.Request request, Principal principal) {
        // Principal에서 applicantId 파생 (JWT subject = email)
        Long applicantId;
        String applicantName;
        if (principal != null) {
            applicantId = resolveUserId(principal);
            applicantName = resolveUserName(principal, applicantId);
            log.info("[Application] Resolved applicantId={} from principal={}", applicantId, principal.getName());
        } else {
            // 하위 호환성 fallback (내부 호출 등)
            applicantId = request.getApplicantId();
            applicantName = request.getApplicantName();
        }

        // 본인인증(소셜 연동) 여부 확인
        if (!userService.isSocialVerified(applicantId)) {
            throw new com.example.common.exception.IdentityVerificationRequiredException(
                    "메이트에 신청하려면 카카오 또는 네이버 계정 연동이 필요합니다.");
        }

        // 중복 신청 체크
        applicationRepository.findByPartyIdAndApplicantId(request.getPartyId(), applicantId)
                .ifPresent(app -> {
                    throw new DuplicateApplicationException(request.getPartyId(), applicantId);
                });

        // 재신청 차단: 거절된 신청이 있는지 확인
        if (applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(
                request.getPartyId(), applicantId)) {
            throw new InvalidApplicationStatusException("거절된 파티에 다시 신청할 수 없습니다.");
        }

        // 최대 대기 신청 수 체크 (10건 제한)
        long pendingCount = applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(
                request.getPartyId());
        if (pendingCount >= 10) {
            throw new InvalidApplicationStatusException("이 파티의 대기 중인 신청이 최대(10건)에 도달했습니다.");
        }

        // 파티 존재 여부 확인
        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(java.util.Objects.requireNonNull(request.getPartyId())));
        // 파티가 가득 찼는지 확인
        if (party.getCurrentParticipants() >= party.getMaxParticipants()) {
            throw new PartyFullException(request.getPartyId());
        }

        Instant now = Instant.now();
        Instant deadline = now.plus(48, ChronoUnit.HOURS);

        // Server-side ticket verification: validate the token from OCR service
        boolean isTicketVerified = false;
        String token = request.getVerificationToken();
        if (token != null && !token.isBlank()) {
            com.example.kbo.dto.TicketInfo ticketInfo = ticketVerificationTokenStore.consumeToken(token);
            if (ticketInfo != null) {
                isTicketVerified = validateTicketMatch(ticketInfo, party);
                if (!isTicketVerified) {
                    log.warn("[Application] Ticket match failed for party={} applicant={}",
                            request.getPartyId(), applicantId);
                }
            } else {
                log.warn("[Application] Invalid/expired verification token for party={}", request.getPartyId());
            }
        }

        // Server-side badge resolution — 클라이언트 값 무시
        Party.BadgeType badge = resolveBadge(applicantId, isTicketVerified);

        PartyApplication application = PartyApplication.builder()
                .partyId(request.getPartyId())
                .applicantId(applicantId)
                .applicantName(applicantName)
                .applicantBadge(badge)
                .applicantRating(request.getApplicantRating() != null ? request.getApplicantRating() : 5.0)
                .message(request.getMessage())
                .depositAmount(request.getDepositAmount())
                .paymentType(request.getPaymentType())
                .ticketVerified(isTicketVerified)
                .ticketImageUrl(request.getTicketImageUrl())
                .isPaid(false)
                .isApproved(false)
                .isRejected(false)
                .responseDeadline(deadline)
                .build();

        // 전액 결제인 경우 자동 승인
        if (request.getPaymentType() == PartyApplication.PaymentType.FULL) {
            application.setIsApproved(true);
            application.setApprovedAt(Instant.now());
            partyService.incrementParticipants(request.getPartyId());
        }

        PartyApplication savedApplication = applicationRepository.save(application);

        notificationService.createNotification(
                party.getHostId(),
                Notification.NotificationType.APPLICATION_RECEIVED,
                "새로운 참여 신청",
                applicantName + "님이 파티에 참여 신청했습니다.",
                savedApplication.getPartyId());

        return PartyApplicationDTO.Response.from(savedApplication);
    }

    /**
     * 하위 호환용 (principal 없이 호출되는 내부 용도)
     */
    @Transactional
    public PartyApplicationDTO.Response createApplication(PartyApplicationDTO.Request request) {
        return createApplication(request, null);
    }

    // 파티별 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApplicationsByPartyId(Long partyId, Principal principal) {
        Long requesterId = resolveUserId(principal);
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        if (!party.getHostId().equals(requesterId)) {
            throw new UnauthorizedAccessException("파티 호스트만 신청 목록을 조회할 수 있습니다.");
        }

        return applicationRepository.findByPartyId(partyId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 특정 파티에 대한 내 신청 단건 조회
    @Transactional(readOnly = true)
    public PartyApplicationDTO.Response getMyApplicationByPartyId(Long partyId, Principal principal) {
        Long applicantId = resolveUserId(principal);
        return applicationRepository.findByPartyIdAndApplicantId(partyId, applicantId)
                .map(PartyApplicationDTO.Response::from)
                .orElse(null);
    }

    // 신청자별 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApplicationsByApplicantId(Long applicantId) {
        return applicationRepository.findByApplicantId(applicantId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 내 신청 목록 조회 (Principal 기반)
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getMyApplications(Principal principal) {
        Long userId = resolveUserId(principal);
        return getApplicationsByApplicantId(userId);
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
    public PartyApplicationDTO.Response approveApplication(Long applicationId, Principal principal) {
        Long hostId = resolveUserId(principal);
        PartyApplication application = applicationRepository.findById(java.util.Objects.requireNonNull(applicationId))
                .orElseThrow(() -> new PartyApplicationNotFoundException(applicationId));

        Party party = partyRepository.findById(application.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(application.getPartyId()));

        if (!party.getHostId().equals(hostId)) {
            throw new UnauthorizedAccessException("파티 호스트만 신청을 승인할 수 있습니다.");
        }

        if (application.getIsApproved()) {
            throw new InvalidApplicationStatusException("이미 승인된 신청입니다.");
        }

        if (application.getIsRejected()) {
            throw new InvalidApplicationStatusException("거절된 신청은 승인할 수 없습니다.");
        }

        application.setIsApproved(true);
        application.setApprovedAt(Instant.now());

        // 파티 참여 인원 증가
        partyService.incrementParticipants(application.getPartyId());

        PartyApplication savedApplication = applicationRepository.save(application);

        // 신청자에게 알림 발송
        notificationService.createNotification(
                application.getApplicantId(),
                Notification.NotificationType.APPLICATION_APPROVED,
                "참여 승인 완료",
                "파티 참여 신청이 승인되었습니다!",
                application.getPartyId());

        return PartyApplicationDTO.Response.from(savedApplication);
    }

    // 신청 거절
    @Transactional
    public PartyApplicationDTO.Response rejectApplication(Long applicationId, Principal principal) {
        Long hostId = resolveUserId(principal);
        PartyApplication application = applicationRepository.findById(java.util.Objects.requireNonNull(applicationId))
                .orElseThrow(() -> new PartyApplicationNotFoundException(applicationId));

        Party party = partyRepository.findById(application.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(application.getPartyId()));

        if (!party.getHostId().equals(hostId)) {
            throw new UnauthorizedAccessException("파티 호스트만 신청을 거절할 수 있습니다.");
        }

        if (application.getIsApproved()) {
            throw new InvalidApplicationStatusException("승인된 신청은 거절할 수 없습니다.");
        }

        if (application.getIsRejected()) {
            throw new InvalidApplicationStatusException("이미 거절된 신청입니다.");
        }

        application.setIsRejected(true);
        application.setRejectedAt(Instant.now());

        PartyApplication savedApplication = applicationRepository.save(application);
        // 신청자에게 알림 발송
        notificationService.createNotification(
                application.getApplicantId(),
                Notification.NotificationType.APPLICATION_REJECTED,
                "참여 신청 거절",
                "파티 참여 신청이 거절되었습니다.",
                application.getPartyId());

        return PartyApplicationDTO.Response.from(savedApplication);
    }

    // 신청 취소 (신청자가 취소) — Principal 기반
    @Transactional
    public void cancelApplication(Long applicationId, Principal principal) {
        // Principal에서 applicantId 파생
        Long applicantId = resolveUserId(principal);

        PartyApplication application = applicationRepository.findById(java.util.Objects.requireNonNull(applicationId))
                .orElseThrow(() -> new PartyApplicationNotFoundException(applicationId));

        // 본인 확인
        if (!application.getApplicantId().equals(applicantId)) {
            throw new UnauthorizedAccessException("본인의 신청만 취소할 수 있습니다.");
        }

        // 거절된 신청은 취소 불필요
        if (application.getIsRejected()) {
            throw new InvalidApplicationStatusException("이미 거절된 신청입니다.");
        }

        Party party = partyRepository.findById(java.util.Objects.requireNonNull(application.getPartyId()))
                .orElseThrow(() -> new PartyNotFoundException(application.getPartyId()));

        // 승인 전: 자유롭게 취소 가능 (전액 환불)
        if (!application.getIsApproved()) {
            applicationRepository.delete(application);
            return;
        }

        // 승인 후: 경기 D-1까지만 취소 가능
        LocalDate today = LocalDate.now();
        long daysUntilGame = ChronoUnit.DAYS.between(today, party.getGameDate());

        if (daysUntilGame < 1) {
            throw new InvalidApplicationStatusException("경기 하루 전부터는 취소할 수 없습니다.");
        }

        // 체크인 이후에는 취소 불가
        if (party.getStatus() == Party.PartyStatus.CHECKED_IN ||
                party.getStatus() == Party.PartyStatus.COMPLETED) {
            throw new InvalidApplicationStatusException("체크인 이후에는 참여를 취소할 수 없습니다.");
        }

        // 승인된 신청 취소 시 참여 인원 감소
        partyService.decrementParticipants(application.getPartyId());

        // 신청 삭제
        applicationRepository.delete(application);
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
        }
        return userService.getUserIdByEmail(principal.getName());
    }

    private String resolveUserName(Principal principal, Long userId) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            try {
                return userService.findUserByEmail(principal.getName()).getName();
            } catch (RuntimeException ignored) {
                // principal name이 email이 아닌 userId일 수 있어 ID 조회로 fallback
            }
        }
        return userService.findUserById(userId).getName();
    }

    // Verify that ticket information matches the party's game details
    /**
     * 서버 사이드 배지 결정.
     * - 티켓 인증 성공 → VERIFIED
     * - 과거 승인된 신청 3건 이상 → TRUSTED
     * - 그 외 → NEW
     */
    private Party.BadgeType resolveBadge(Long applicantId, boolean isTicketVerified) {
        if (isTicketVerified) {
            return Party.BadgeType.VERIFIED;
        }
        // 과거 승인된 신청 수 기반 TRUSTED 부여
        long approvedCount = applicationRepository.countByApplicantIdAndIsApprovedTrue(applicantId);
        if (approvedCount >= 3) {
            return Party.BadgeType.TRUSTED;
        }
        return Party.BadgeType.NEW;
    }

    /**
     * OCR 결과와 파티 정보 매칭 검증.
     * TicketTeamNormalizer를 사용하여 팀 이름을 표준 teamId로 정규화 후 비교합니다.
     */
    private boolean validateTicketMatch(com.example.kbo.dto.TicketInfo ticketInfo, Party party) {
        if (ticketInfo == null)
            return false;

        // 날짜 매칭
        boolean dateMatch = false;
        try {
            if (ticketInfo.getDate() != null && !ticketInfo.getDate().isBlank()) {
                if (ticketInfo.getDate().equals(party.getGameDate().toString())) {
                    dateMatch = true;
                } else {
                    LocalDate ticketDate = LocalDate.parse(ticketInfo.getDate());
                    dateMatch = ticketDate.isEqual(party.getGameDate());
                }
            }
        } catch (Exception e) {
            log.warn("[TicketMatch] Date parsing failed for ticket date: {}", ticketInfo.getDate());
            if (ticketInfo.getDate() != null && ticketInfo.getDate().equals(party.getGameDate().toString())) {
                dateMatch = true;
            }
        }

        if (!dateMatch) {
            log.info("[TicketMatch] Date mismatch: ticket={} party={}",
                    ticketInfo.getDate(), party.getGameDate());
            return false;
        }

        // 팀 매칭 (TicketTeamNormalizer로 양쪽 모두 정규화 후 비교)
        String normalizedTicketHome = TicketTeamNormalizer.normalize(ticketInfo.getHomeTeam());
        String normalizedTicketAway = TicketTeamNormalizer.normalize(ticketInfo.getAwayTeam());
        String normalizedPartyHome = TicketTeamNormalizer.normalize(party.getHomeTeam());
        String normalizedPartyAway = TicketTeamNormalizer.normalize(party.getAwayTeam());

        boolean homeMatch = normalizedTicketHome != null && normalizedTicketHome.equalsIgnoreCase(normalizedPartyHome);
        boolean awayMatch = normalizedTicketAway != null && normalizedTicketAway.equalsIgnoreCase(normalizedPartyAway);

        if (!homeMatch || !awayMatch) {
            log.info("[TicketMatch] Team mismatch: ticket home={}/away={} vs party home={}/away={}",
                    normalizedTicketHome, normalizedTicketAway, normalizedPartyHome, normalizedPartyAway);
        }

        return homeMatch && awayMatch;
    }

}
