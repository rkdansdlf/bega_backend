package com.example.mate.service;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.CancelReasonType;
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

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final PaymentTransactionService paymentTransactionService;

    @Value("${payment.selling.enforced:true}")
    private boolean sellingPaymentEnforced;

    /**
     * 신청 생성 (Principal 기반 — 컨트롤러에서 호출)
     * applicantId는 JWT principal에서 파생하여 스푸핑 방지
     */
    @Transactional
    public PartyApplicationDTO.Response createApplication(PartyApplicationDTO.Request request, Principal principal) {
        if (request.getPaymentType() == PartyApplication.PaymentType.FULL && sellingPaymentEnforced) {
            throw new InvalidApplicationStatusException("전액 결제 신청은 결제 승인 API를 통해서만 생성할 수 있습니다.");
        }
        ApplicationCreationContext context = prepareCreationContext(request, principal);

        PartyApplication application = PartyApplication.builder()
                .partyId(request.getPartyId())
                .applicantId(context.applicantId())
                .applicantName(context.applicantName())
                .applicantBadge(context.applicantBadge())
                .applicantRating(request.getApplicantRating() != null ? request.getApplicantRating() : 5.0)
                .message(request.getMessage())
                .depositAmount(request.getDepositAmount())
                .paymentType(request.getPaymentType())
                .ticketVerified(context.ticketVerified())
                .ticketImageUrl(request.getTicketImageUrl())
                .isPaid(false)
                .isApproved(false)
                .isRejected(false)
                .responseDeadline(context.deadline())
                .build();

        PartyApplication savedApplication = Objects.requireNonNull(applicationRepository.save(application));
        notifyHostOnApplication(context.party(), context.applicantName(), savedApplication.getPartyId());
        return Objects.requireNonNull(PartyApplicationDTO.Response.from(savedApplication));
    }

    /**
     * 하위 호환용 (principal 없이 호출되는 내부 용도)
     */
    @Transactional
    public PartyApplicationDTO.Response createApplication(PartyApplicationDTO.Request request) {
        return createApplication(request, null);
    }

    /**
     * Toss Payments 결제 승인 후 파티 신청 생성.
     * isPaid=true로 저장하며, paymentKey와 orderId를 기록합니다.
     */
    @Transactional
    public PartyApplicationDTO.Response createApplicationWithPayment(
            PartyApplicationDTO.Request request,
            Principal principal,
            String paymentKey,
            String orderId,
            Integer confirmedAmount,
            PartyApplication.PaymentType forcedPaymentType) {
        if (orderId == null || orderId.isBlank()) {
            throw new InvalidApplicationStatusException("orderId는 필수입니다.");
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new InvalidApplicationStatusException("paymentKey는 필수입니다.");
        }
        if (confirmedAmount == null || confirmedAmount <= 0) {
            throw new InvalidApplicationStatusException("결제 금액이 올바르지 않습니다.");
        }

        Long applicantId = resolveUserId(principal);
        PartyApplication applicantIdExisting = resolveExistingPaymentApplication(orderId, paymentKey, applicantId);
        if (applicantIdExisting != null) {
            return Objects.requireNonNull(PartyApplicationDTO.Response.from(applicantIdExisting));
        }

        ApplicationCreationContext context = prepareCreationContext(request, principal);
        PartyApplication contextExisting = resolveExistingPaymentApplication(orderId, paymentKey,
                context.applicantId());
        if (contextExisting != null) {
            return Objects.requireNonNull(PartyApplicationDTO.Response.from(contextExisting));
        }

        PartyApplication application = PartyApplication.builder()
                .partyId(request.getPartyId())
                .applicantId(context.applicantId())
                .applicantName(context.applicantName())
                .applicantBadge(context.applicantBadge())
                .applicantRating(request.getApplicantRating() != null ? request.getApplicantRating() : 5.0)
                .message(request.getMessage() != null ? request.getMessage() : "함께 즐거운 관람 부탁드립니다!")
                .depositAmount(confirmedAmount)
                .paymentType(forcedPaymentType)
                .ticketVerified(context.ticketVerified())
                .ticketImageUrl(request.getTicketImageUrl())
                .isPaid(true)
                .isApproved(false)
                .isRejected(false)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .responseDeadline(context.deadline())
                .build();

        if (forcedPaymentType == PartyApplication.PaymentType.FULL) {
            if (sellingPaymentEnforced && context.party().getStatus() != Party.PartyStatus.SELLING) {
                throw new InvalidApplicationStatusException("SELLING 상태가 아닌 파티는 전액 결제를 지원하지 않습니다.");
            }
            application.setIsApproved(true);
            application.setApprovedAt(Instant.now());
            partyService.incrementParticipants(Objects.requireNonNull(request.getPartyId()));
        }

        PartyApplication savedApplication = Objects.requireNonNull(applicationRepository.save(application));
        notifyHostOnApplication(context.party(), context.applicantName(), savedApplication.getPartyId());
        return Objects.requireNonNull(PartyApplicationDTO.Response.from(savedApplication));
    }

    // 파티별 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApplicationsByPartyId(Long partyId, Principal principal) {
        Long requesterId = resolveUserId(principal);
        Party party = partyRepository.findById(java.util.Objects.requireNonNull(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        if (!party.getHostId().equals(requesterId)) {
            throw new UnauthorizedAccessException("파티 호스트만 신청 목록을 조회할 수 있습니다.");
        }

        List<PartyApplicationDTO.Response> responses = Objects
                .requireNonNull(applicationRepository.findByPartyId(partyId).stream()
                        .map(PartyApplicationDTO.Response::from)
                        .collect(Collectors.toList()));
        paymentTransactionService.enrichResponses(responses);
        return responses;
    }

    // 특정 파티에 대한 내 신청 단건 조회
    @Transactional(readOnly = true)
    public PartyApplicationDTO.Response getMyApplicationByPartyId(Long partyId, Principal principal) {
        Long applicantId = resolveUserId(principal);
        PartyApplicationDTO.Response response = applicationRepository.findByPartyIdAndApplicantId(partyId, applicantId)
                .map(PartyApplicationDTO.Response::from)
                .orElse(null);
        paymentTransactionService.enrichResponse(response);
        return response;
    }

    // 신청자별 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApplicationsByApplicantId(Long applicantId) {
        return Objects.requireNonNull(applicationRepository.findByApplicantId(applicantId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList()));
    }

    // 내 신청 목록 조회 (Principal 기반)
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getMyApplications(Principal principal) {
        Long userId = resolveUserId(principal);
        return Objects.requireNonNull(getApplicationsByApplicantId(userId));
    }

    // 대기중인 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getPendingApplications(Long partyId) {
        return Objects.requireNonNull(
                applicationRepository.findByPartyIdAndIsApprovedFalseAndIsRejectedFalse(partyId).stream()
                        .map(PartyApplicationDTO.Response::from)
                        .collect(Collectors.toList()));
    }

    // 승인된 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getApprovedApplications(Long partyId) {
        return Objects.requireNonNull(applicationRepository.findByPartyIdAndIsApprovedTrue(partyId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList()));
    }

    // 거절된 신청 목록 조회
    @Transactional(readOnly = true)
    public List<PartyApplicationDTO.Response> getRejectedApplications(Long partyId) {
        return Objects.requireNonNull(applicationRepository.findByPartyIdAndIsRejectedTrue(partyId).stream()
                .map(PartyApplicationDTO.Response::from)
                .collect(Collectors.toList()));
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
        paymentTransactionService.requestSettlementOnApproval(savedApplication);

        // 신청자에게 알림 발송
        notificationService.createNotification(
                application.getApplicantId(),
                Notification.NotificationType.APPLICATION_APPROVED,
                "참여 승인 완료",
                "파티 참여 신청이 승인되었습니다!",
                application.getPartyId());

        PartyApplicationDTO.Response response = PartyApplicationDTO.Response.from(savedApplication);
        paymentTransactionService.enrichResponse(response);
        return response;
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

        if (Boolean.TRUE.equals(application.getIsPaid())) {
            try {
                paymentTransactionService.processCancellation(
                        application,
                        PartyApplicationDTO.CancelRequest.builder()
                                .cancelReasonType(CancelReasonType.SELLER_CHANGED_MIND)
                                .cancelMemo("호스트 신청 거절")
                                .build());
            } catch (RuntimeException e) {
                log.error("[Application] 거절 신청 환불 실패: applicationId={}", application.getId(), e);
                // 환불 실패해도 거절 자체는 진행
            }
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

        PartyApplicationDTO.Response response = PartyApplicationDTO.Response.from(savedApplication);
        paymentTransactionService.enrichResponse(response);
        return response;
    }

    // 신청 취소 (신청자가 취소) — Principal 기반
    @Transactional
    public void cancelApplication(Long applicationId, Principal principal) {
        cancelApplication(applicationId, principal, PartyApplicationDTO.CancelRequest.builder()
                .cancelReasonType(CancelReasonType.BUYER_CHANGED_MIND)
                .build());
    }

    @Transactional
    public PartyApplicationDTO.CancelResponse cancelApplication(
            Long applicationId,
            Principal principal,
            PartyApplicationDTO.CancelRequest cancelRequest) {
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

        // 승인 전: 자유롭게 취소 가능
        if (!application.getIsApproved()) {
            PartyApplicationDTO.CancelResponse cancelResponse = paymentTransactionService
                    .processCancellation(application, cancelRequest);
            applicationRepository.delete(application);
            return Objects.requireNonNull(cancelResponse);
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

        PartyApplicationDTO.CancelResponse cancelResponse = paymentTransactionService.processCancellation(application,
                cancelRequest);

        // 승인된 신청 취소 시 참여 인원 감소
        partyService.decrementParticipants(application.getPartyId());

        // 신청 삭제
        applicationRepository.delete(application);
        return Objects.requireNonNull(cancelResponse);
    }

    @Transactional(readOnly = true)
    public PartyApplication getApplicationEntity(Long applicationId) {
        return Objects.requireNonNull(applicationRepository.findById(java.util.Objects.requireNonNull(applicationId))
                .orElseThrow(() -> new PartyApplicationNotFoundException(applicationId)));
    }

    private ApplicationCreationContext prepareCreationContext(PartyApplicationDTO.Request request,
            Principal principal) {
        Long applicantId;
        String applicantName;
        if (principal != null) {
            applicantId = resolveUserId(principal);
            applicantName = resolveUserName(principal, applicantId);
            log.info("[Application] Resolved applicantId={} from principal={}", applicantId, principal.getName());
        } else {
            applicantId = request.getApplicantId();
            applicantName = request.getApplicantName();
        }

        if (!userService.isSocialVerified(applicantId)) {
            throw new com.example.common.exception.IdentityVerificationRequiredException(
                    "메이트에 신청하려면 카카오 또는 네이버 계정 연동이 필요합니다.");
        }

        applicationRepository.findByPartyIdAndApplicantId(request.getPartyId(), applicantId)
                .ifPresent(app -> {
                    throw new DuplicateApplicationException(request.getPartyId(), applicantId);
                });

        if (applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(
                request.getPartyId(), applicantId)) {
            throw new InvalidApplicationStatusException("거절된 파티에 다시 신청할 수 없습니다.");
        }

        long pendingCount = applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(
                request.getPartyId());
        if (pendingCount >= 10) {
            throw new InvalidApplicationStatusException("이 파티의 대기 중인 신청이 최대(10건)에 도달했습니다.");
        }

        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(java.util.Objects.requireNonNull(request.getPartyId())));
        if (party.getCurrentParticipants() >= party.getMaxParticipants()) {
            throw new PartyFullException(request.getPartyId());
        }

        boolean ticketVerified = consumeAndValidateTicketToken(request, party, applicantId);
        Party.BadgeType badge = resolveBadge(applicantId, ticketVerified);
        Instant deadline = Instant.now().plus(48, ChronoUnit.HOURS);
        return new ApplicationCreationContext(applicantId, applicantName, party, ticketVerified, badge, deadline);
    }

    private boolean consumeAndValidateTicketToken(
            PartyApplicationDTO.Request request,
            Party party,
            Long applicantId) {
        boolean isTicketVerified = false;
        String token = request.getVerificationToken();
        if (token == null || token.isBlank()) {
            return false;
        }

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
        return isTicketVerified;
    }

    private void notifyHostOnApplication(Party party, String applicantName, Long partyId) {
        notificationService.createNotification(
                party.getHostId(),
                Notification.NotificationType.APPLICATION_RECEIVED,
                "새로운 참여 신청",
                applicantName + "님이 파티에 참여 신청했습니다.",
                partyId);
    }

    private PartyApplication resolveExistingPaymentApplication(String orderId, String paymentKey, Long applicantId) {
        PartyApplication byOrderId = resolveExistingByOrderId(orderId, applicantId);
        if (byOrderId != null) {
            return byOrderId;
        }
        return resolveExistingByPaymentKey(paymentKey, applicantId);
    }

    private PartyApplication resolveExistingByOrderId(String orderId, Long applicantId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        PartyApplication existing = applicationRepository.findByOrderId(orderId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getApplicantId().equals(applicantId)) {
            return existing;
        }
        throw new InvalidApplicationStatusException("이미 사용된 orderId 입니다.");
    }

    private PartyApplication resolveExistingByPaymentKey(String paymentKey, Long applicantId) {
        if (paymentKey == null || paymentKey.isBlank()) {
            return null;
        }
        PartyApplication existing = applicationRepository.findByPaymentKey(paymentKey).orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getApplicantId().equals(applicantId)) {
            return existing;
        }
        throw new InvalidApplicationStatusException("이미 사용된 paymentKey 입니다.");
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

    private record ApplicationCreationContext(
            Long applicantId,
            String applicantName,
            Party party,
            boolean ticketVerified,
            Party.BadgeType applicantBadge,
            Instant deadline) {
    }

}
