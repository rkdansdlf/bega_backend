package com.example.mate.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.util.HandleNormalizer;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.UserNotFoundException;
import com.example.homepage.FeaturedMateCardDto;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.kbo.util.TeamCodeNormalizer;
import com.example.kbo.util.TicketTeamNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PartyService {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final PartyApplicationRepository applicationRepository;
    private final PartyMapper partyMapper;
    private final PartyFavoriteService partyFavoriteService;
    private final UserProviderRepository userProviderRepository;
    private final PublicVisibilityVerifier publicVisibilityVerifier;
    private final TicketVerificationTokenStore ticketVerificationTokenStore;
    private final com.example.notification.service.NotificationService notificationService;
    private final MateHistoryMetricsService mateHistoryMetricsService;
    @Value("${mate.auth.require-social-verification:true}")
    private boolean requireSocialVerification;

    @Transactional
    public PartyDTO.Response createParty(@NonNull PartyDTO.Request request, Principal principal) {
        return createParty(request, getUserIdFromPrincipal(principal));
    }

    @Transactional
    public PartyDTO.Response createParty(@NonNull PartyDTO.Request request, @NonNull Long hostId) {
        UserEntity hostUser = userRepository.findById(hostId)
                .orElseThrow(() -> new UserNotFoundException(hostId));
        boolean socialVerified = isSocialVerified(hostId);

        // 본인인증(소셜 연동) 여부 확인
        if (requireSocialVerification && !socialVerified) {
            throw new com.example.common.exception.IdentityVerificationRequiredException(
                    "메이트를 생성하려면 카카오 또는 네이버 계정 연동이 필요합니다.");
        }

        // 사용자 정보에서 profileImageUrl, favoriteTeam 가져오기
        String hostProfileImageUrl = hostUser.getProfileImageUrl();
        if (hostProfileImageUrl != null && partyMapper.isLegacyOrInvalidProfileValue(hostProfileImageUrl)) {
            hostProfileImageUrl = null;
        }
        String hostFavoriteTeam = hostUser.getFavoriteTeamId();
        String hostName = hostUser.getName();
        Party.BadgeType hostBadge = resolveHostBadge(hostId, socialVerified);
        MateContentPolicyValidator.validatePartyDescription(request.getDescription());
        TicketInfo verifiedTicket = consumeAndValidateTicketToken(request);
        Party.CheeringSide cheeringSide = Objects.requireNonNull(request.getCheeringSide());
        String normalizedHomeTeam = normalizeTeamCode(request.getHomeTeam());
        String normalizedAwayTeam = normalizeTeamCode(request.getAwayTeam());
        String resolvedTeamId = resolveTeamId(cheeringSide, normalizedHomeTeam, normalizedAwayTeam);

        Party party = Party.builder()
                .hostId(hostId)
                .hostName(hostName)
                .hostProfileImageUrl(hostProfileImageUrl)
                .hostFavoriteTeam(hostFavoriteTeam)
                .hostBadge(hostBadge)
                .teamId(resolvedTeamId)
                .cheeringSide(cheeringSide)
                .gameDate(request.getGameDate())
                .gameTime(request.getGameTime())
                .stadium(request.getStadium())
                .homeTeam(normalizedHomeTeam)
                .awayTeam(normalizedAwayTeam)
                .section(request.getSection())
                .seatDetail(request.getSeatDetail())
                .maxParticipants(request.getMaxParticipants())
                .currentParticipants(1) // 호스트 포함
                .description(request.getDescription())
                .searchText(buildSearchText(
                        request.getStadium(),
                        normalizedHomeTeam,
                        normalizedAwayTeam,
                        request.getSection(),
                        request.getSeatDetail(),
                        hostName,
                        request.getDescription()))
                .ticketVerified(verifiedTicket != null)
                .ticketImageUrl(null)
                .ticketPrice(request.getTicketPrice())
                .reservationDepositAmount(request.getReservationDepositAmount())
                .reservationNumber(request.getReservationNumber())
                .status(Party.PartyStatus.PENDING)
                .build();

        Party savedParty = partyRepository.save(party);

        return Objects.requireNonNull(partyMapper.toResponse(savedParty));
    }

    // 모든 파티 조회 (검색 및 필터링 통합)
    @Transactional(readOnly = true)
    public Page<PartyDTO.PublicResponse> getAllParties(String teamId, String stadium, LocalDate gameDate,
            String searchQuery,
            Pageable pageable, Party.PartyStatus status, Long currentUserId) {
        String normalizedTeamId = TeamCodeNormalizer.normalize(teamId);
        String normalizedSearchQuery = normalizeSearchQuery(searchQuery);

        List<Party.PartyStatus> excludedStatuses = List.of(
                Party.PartyStatus.CHECKED_IN,
                Party.PartyStatus.COMPLETED);

        // 빈 문자열을 null로 변환하여 쿼리에서 무시되도록 함
        if (stadium != null) {
            stadium = stadium.trim();
        }
        if (stadium != null && stadium.isBlank())
            stadium = null;
        if (normalizedTeamId != null && normalizedTeamId.isBlank())
            normalizedTeamId = null;

        Page<Party> parties = partyRepository.findVisiblePublicPartiesWithFilter(
                normalizedTeamId,
                stadium,
                gameDate,
                normalizedSearchQuery,
                excludedStatuses,
                status,
                currentUserId,
                pageable);
        List<PartyDTO.PublicResponse> visibleContent = partyMapper.toPublicResponses(parties.getContent());
        applyFavoriteState(visibleContent, currentUserId);
        return new org.springframework.data.domain.PageImpl<>(visibleContent, pageable, parties.getTotalElements());
    }

    // 파티 ID로 조회
    @Transactional(readOnly = true)
    public PartyDTO.PublicResponse getPartyById(@NonNull Long id, Long currentUserId) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));
        validatePartyVisibility(party, currentUserId);
        PartyDTO.PublicResponse response = Objects.requireNonNull(partyMapper.toPublicResponse(party));
        response.setFavorited(currentUserId != null && partyFavoriteService.isFavorited(currentUserId, id));
        response.setMembers(buildPartyMembers(party));
        return response;
    }

    // 참여현황 표시용 승인 멤버(호스트 + 승인 신청자). 프라이버시: 이니셜+아바타만 노출.
    private List<PartyDTO.MemberSummary> buildPartyMembers(Party party) {
        List<PartyDTO.MemberSummary> members = new ArrayList<>();
        members.add(PartyDTO.MemberSummary.builder()
                .initial(toMemberInitial(party.getHostName()))
                .profileImageUrl(cleanProfileImageUrl(party.getHostProfileImageUrl()))
                .role("호스트")
                .host(true)
                .build());

        List<PartyApplication> approved = applicationRepository.findByPartyIdAndIsApprovedTrue(party.getId());
        if (approved == null || approved.isEmpty()) {
            return members;
        }

        List<Long> applicantIds = approved.stream()
                .map(PartyApplication::getApplicantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> imageByUserId = userRepository.findAllById(applicantIds).stream()
                .collect(Collectors.toMap(UserEntity::getId,
                        user -> cleanProfileImageUrl(user.getProfileImageUrl()),
                        (first, second) -> first));

        for (PartyApplication application : approved) {
            members.add(PartyDTO.MemberSummary.builder()
                    .initial(toMemberInitial(application.getApplicantName()))
                    .profileImageUrl(application.getApplicantId() == null ? null : imageByUserId.get(application.getApplicantId()))
                    .role("메이트")
                    .host(false)
                    .build());
        }
        return members;
    }

    private String toMemberInitial(String name) {
        if (name == null) {
            return "M";
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "M" : trimmed.substring(0, 1);
    }

    private String cleanProfileImageUrl(String url) {
        return (url != null && partyMapper.isLegacyOrInvalidProfileValue(url)) ? null : url;
    }

    // 상태별 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.PublicResponse> getPartiesByStatus(Party.PartyStatus status, Long currentUserId) {
        List<Party> visibleParties = filterVisiblePublicParties(
                partyRepository.findByStatusOrderByCreatedAtDesc(status),
                currentUserId);
        List<PartyDTO.PublicResponse> responses = partyMapper.toPublicResponses(visibleParties);
        applyFavoriteState(responses, currentUserId);
        return responses;
    }

    @Transactional(readOnly = true)
    public List<PartyDTO.PublicResponse> getPartiesByHostHandle(String handle, Long currentUserId) {
        UserEntity host = HandleNormalizer.candidates(handle).stream()
                .map(userRepository::findByHandle)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException("handle", handle));
        publicVisibilityVerifier.validate(host, currentUserId, "파티");
        Long hostId = host.getId();
        List<PartyDTO.PublicResponse> responses = partyMapper.toPublicResponses(partyRepository.findByHostId(hostId));
        applyFavoriteState(responses, currentUserId);
        return responses;
    }

    // 검색
    @Transactional(readOnly = true)
    public List<PartyDTO.PublicResponse> searchParties(String query, Long currentUserId) {
        List<Party> visibleParties = filterVisiblePublicParties(partyRepository.searchParties(query), currentUserId);
        List<PartyDTO.PublicResponse> responses = partyMapper.toPublicResponses(visibleParties);
        applyFavoriteState(responses, currentUserId);
        return responses;
    }

    // 경기 날짜 이후 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.PublicResponse> getUpcomingParties(Long currentUserId) {
        LocalDate today = LocalDate.now();
        List<Party> visibleParties = filterVisiblePublicParties(
                partyRepository.findByGameDateAfterOrderByGameDateAsc(today),
                currentUserId);
        List<PartyDTO.PublicResponse> responses = partyMapper.toPublicResponses(visibleParties);
        applyFavoriteState(responses, currentUserId);
        return responses;
    }

    @Transactional(readOnly = true)
    public List<FeaturedMateCardDto> getFeaturedMateCards(LocalDate baseDate, int limit) {
        int targetLimit = Math.max(1, limit);
        Pageable pageable = PageRequest.of(
                0,
                Math.max(targetLimit * 3, targetLimit),
                Sort.by(Sort.Direction.ASC, "gameDate")
                        .and(Sort.by(Sort.Direction.ASC, "gameTime"))
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Page<Party> candidatePage = partyRepository.findByStatusAndGameDateGreaterThanEqual(
                Party.PartyStatus.PENDING,
                baseDate,
                pageable);

        return filterVisiblePublicParties(candidatePage.getContent(), null).stream()
                .limit(targetLimit)
                .map(partyMapper::toFeaturedMateCard)
                .toList();
    }

    // 파티 업데이트
    @Transactional
    public PartyDTO.Response updateParty(@NonNull Long id, @NonNull PartyDTO.UpdateRequest request,
            Principal principal) {
        return updateParty(id, request, getUserIdFromPrincipal(principal));
    }

    @Transactional
    public PartyDTO.Response updateParty(@NonNull Long id, @NonNull PartyDTO.UpdateRequest request,
            @NonNull Long requesterId) {
        Party party = partyRepository.findByIdAndHostIdForUpdate(id, requesterId)
                .orElseThrow(() -> new PartyNotFoundException(id));

        boolean hasApprovedApplications = applicationRepository.countByPartyIdAndIsApprovedTrue(id) > 0;
        Party.PartyStatus originalStatus = party.getStatus();
        boolean sellingConversionRequested = request.getStatus() == Party.PartyStatus.SELLING;

        validateHostStatusChange(originalStatus, request.getStatus());

        if (request.getDescription() != null) {
            MateContentPolicyValidator.validatePartyDescription(request.getDescription());
            party.setDescription(request.getDescription());
        }

        if (sellingConversionRequested) {
            validateSellingConversionRequest(party, request, hasApprovedApplications, originalStatus);
            party.setStatus(Party.PartyStatus.SELLING);
            party.setPrice(request.getPrice());
            if (request.getTicketPrice() != null) {
                party.setTicketPrice(request.getTicketPrice());
            }
        } else {
            if (request.getStatus() != null) {
                party.setStatus(request.getStatus());
            }

            // price, section, maxParticipants, ticketPrice는 PENDING 상태이고 승인된 신청이 없을 때만 변경
            // 가능
            if (party.getStatus() == Party.PartyStatus.PENDING && !hasApprovedApplications) {
                if (request.getPrice() != null) {
                    party.setPrice(request.getPrice());
                }
                if (request.getSection() != null) {
                    party.setSection(request.getSection());
                }
                if (request.getSeatDetail() != null) {
                    party.setSeatDetail(request.getSeatDetail());
                }
                if (request.getMaxParticipants() != null) {
                    if (request.getMaxParticipants() < party.getCurrentParticipants()) {
                        throw new InvalidApplicationStatusException(
                                "최대 참여 인원은 현재 참여 인원(" + party.getCurrentParticipants() + "명) 이상이어야 합니다.");
                    }
                    party.setMaxParticipants(request.getMaxParticipants());
                }
                if (request.getTicketPrice() != null) {
                    party.setTicketPrice(request.getTicketPrice());
                }
                if (request.getReservationDepositAmount() != null) {
                    party.setReservationDepositAmount(request.getReservationDepositAmount());
                }
            } else if (request.getPrice() != null || request.getSection() != null
                    || request.getSeatDetail() != null
                    || request.getMaxParticipants() != null
                    || request.getTicketPrice() != null
                    || request.getReservationDepositAmount() != null) {
                throw new InvalidApplicationStatusException(
                        "승인된 참여자가 있거나 모집 중 상태가 아닌 경우 가격/예약금/좌석/인원을 변경할 수 없습니다.");
            }
        }

        party.setSearchText(buildSearchText(
                party.getStadium(),
                party.getHomeTeam(),
                party.getAwayTeam(),
                party.getSection(),
                party.getSeatDetail(),
                party.getHostName(),
                party.getDescription()));

        Party updatedParty = partyRepository.save(party);
        return Objects.requireNonNull(partyMapper.toResponse(updatedParty));
    }

    private void validateSellingConversionRequest(
            Party party,
            PartyDTO.UpdateRequest request,
            boolean hasApprovedApplications,
            Party.PartyStatus originalStatus) {
        if (originalStatus != Party.PartyStatus.PENDING && originalStatus != Party.PartyStatus.FAILED) {
            throw new InvalidApplicationStatusException(
                    "판매 전환은 모집 중(PENDING) 또는 실패(FAILED) 상태에서만 가능합니다.");
        }
        if (hasApprovedApplications) {
            throw new InvalidApplicationStatusException("승인된 참여자가 있는 파티는 판매 전환할 수 없습니다.");
        }
        if (request.getPrice() == null) {
            throw new InvalidApplicationStatusException("판매 전환 시 price는 필수입니다.");
        }
        if (request.getPrice() < 100) {
            throw new InvalidApplicationStatusException("판매 가격은 최소 100원 이상이어야 합니다.");
        }
        if (request.getSection() != null || request.getSeatDetail() != null || request.getMaxParticipants() != null
                || request.getReservationDepositAmount() != null) {
            throw new InvalidApplicationStatusException("판매 전환 요청에서는 좌석/인원/예약금 변경을 함께 요청할 수 없습니다.");
        }
    }

    private void validateHostStatusChange(
            Party.PartyStatus originalStatus,
            Party.PartyStatus requestedStatus) {
        if (requestedStatus == null || requestedStatus == originalStatus
                || requestedStatus == Party.PartyStatus.SELLING) {
            return;
        }

        throw new InvalidApplicationStatusException(
                "파티 상태는 승인·체크인·수명주기 처리에서만 변경할 수 있습니다.");
    }

    private void applyFavoriteState(List<PartyDTO.PublicResponse> responses, Long currentUserId) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        if (currentUserId == null) {
            responses.forEach(response -> response.setFavorited(false));
            return;
        }

        List<Long> partyIds = responses.stream()
                .map(PartyDTO.PublicResponse::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> favoriteIds = partyFavoriteService.getFavoritePartyIds(currentUserId, partyIds);
        Set<Long> favoritePartyIds = favoriteIds == null ? Set.of() : Set.copyOf(favoriteIds);
        responses.forEach(response ->
                response.setFavorited(response.getId() != null && favoritePartyIds.contains(response.getId())));
    }

    private List<Party> filterVisiblePublicParties(List<Party> parties, Long currentUserId) {
        if (parties == null || parties.isEmpty()) {
            return List.of();
        }

        java.util.Map<Long, UserEntity> hostsById = userRepository.findAllById(
                parties.stream()
                        .map(Party::getHostId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, java.util.function.Function.identity()));

        return parties.stream()
                .filter(party -> {
                    UserEntity host = hostsById.get(party.getHostId());
                    return host == null || publicVisibilityVerifier.canAccess(host, currentUserId);
                })
                .toList();
    }

    private void validatePartyVisibility(Party party, Long currentUserId) {
        if (party.getHostId() == null) {
            return;
        }

        userRepository.findById(party.getHostId())
                .ifPresent(host -> publicVisibilityVerifier.validate(host, currentUserId, "파티"));
    }

    // 파티 참여 인원 증가
    @Transactional
    public PartyDTO.Response incrementParticipants(@NonNull Long id) {
        Party party = partyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        if (party.getCurrentParticipants() >= party.getMaxParticipants()) {
            throw new PartyFullException(id);
        }

        party.setCurrentParticipants(party.getCurrentParticipants() + 1);

        // 파티가 가득 차면 매칭 성공으로 변경
        if (party.getCurrentParticipants().equals(party.getMaxParticipants())) {
            party.setStatus(Party.PartyStatus.MATCHED);
        }

        Party updatedParty = partyRepository.save(party);
        return Objects.requireNonNull(partyMapper.toResponse(updatedParty));
    }

    // 파티 참여 인원 감소
    @Transactional
    public PartyDTO.Response decrementParticipants(@NonNull Long id) {
        Party party = partyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        if (party.getCurrentParticipants() <= 1) {
            throw new InvalidApplicationStatusException("호스트는 파티를 떠날 수 없습니다.");
        }

        party.setCurrentParticipants(party.getCurrentParticipants() - 1);
        party.setStatus(Party.PartyStatus.PENDING);

        Party updatedParty = partyRepository.save(party);
        return Objects.requireNonNull(partyMapper.toResponse(updatedParty));
    }

    @Transactional
    public void deleteParty(@NonNull Long id, Principal principal) {
        deleteParty(id, getUserIdFromPrincipal(principal));
    }

    @Transactional
    public void deleteParty(@NonNull Long id, @NonNull Long requesterId) {
        Party party = partyRepository.findByIdAndHostIdForUpdate(id, requesterId)
                .orElseThrow(() -> new PartyNotFoundException(id));

        // 이미 매칭된 파티는 삭제 불가
        if (party.getStatus() == Party.PartyStatus.MATCHED ||
                party.getStatus() == Party.PartyStatus.CHECKED_IN ||
                party.getStatus() == Party.PartyStatus.COMPLETED) {
            throw new InvalidApplicationStatusException("진행 중이거나 완료된 파티는 삭제할 수 없습니다.");
        }

        // 승인된 신청자가 있는지 확인
        List<PartyApplication> approvedApplications = applicationRepository.findByPartyIdAndIsApprovedTrue(id);

        if (!approvedApplications.isEmpty()) {
            throw new InvalidApplicationStatusException(
                    "승인된 참여자가 있는 파티는 삭제할 수 없습니다. 참여자가 취소하거나 거절 후 삭제해주세요.");
        }

        // 대기 중인 신청들은 함께 삭제
        List<PartyApplication> applicationsToDelete = applicationRepository.findByPartyId(id);
        boolean hasPaidApplication = applicationsToDelete != null && applicationsToDelete.stream()
                .anyMatch(application -> Boolean.TRUE.equals(application.getIsPaid()));
        if (hasPaidApplication) {
            throw new InvalidApplicationStatusException(
                    "결제된 신청이 있는 파티는 삭제할 수 없습니다. 신청을 취소하거나 환불한 뒤 다시 시도해주세요.");
        }
        if (applicationsToDelete != null) {
            applicationRepository.deleteAll(applicationsToDelete);
        }

        partyRepository.delete(party);
    }

    // 사용자가 참여한 모든 파티 조회 (호스트 + 참여자)
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getMyParties(Principal principal) {
        return getMyParties(getUserIdFromPrincipal(principal));
    }

    @Transactional(readOnly = true)
    public Page<PartyDTO.HistoryResponse> getMyPartyHistory(Long userId, String group, Pageable pageable) {
        long startedAtNanos = System.nanoTime();
        try {
            if (userId == null) {
                throw new AuthenticationRequiredException("인증 정보가 없습니다.");
            }

            List<Party.PartyStatus> statuses = resolveMyHistoryStatuses(group);
            Page<PartyDTO.HistoryResponse> response = partyRepository.findMyHistory(userId, statuses, pageable)
                    .map(PartyDTO.HistoryResponse::from);
            mateHistoryMetricsService.recordRequest(group, "success", elapsedNanos(startedAtNanos));
            return response;
        } catch (RuntimeException ex) {
            mateHistoryMetricsService.recordRequest(group, "failure", elapsedNanos(startedAtNanos));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getMyParties(Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증 정보가 없습니다.");
        }

        // 1. 호스트로 생성한 파티
        List<Party> hostedParties = partyRepository.findByHostId(userId);

        // 2. 참여자로 승인된 파티
        List<PartyApplication> approvedApplications = applicationRepository
                .findByApplicantIdAndIsApprovedTrue(userId);

        List<Long> partyIds = approvedApplications.stream()
                .map(PartyApplication::getPartyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Party> participatedParties = partyRepository.findAllById(partyIds);

        // 3. 두 리스트 합치기 (중복 제거)
        List<Party> allParties = new java.util.ArrayList<>(hostedParties);
        participatedParties.forEach(party -> {
            if (allParties.stream().noneMatch(p -> p.getId().equals(party.getId()))) {
                allParties.add(party);
            }
        });

        // 4. 최신순 정렬 (null 필드 대응)
        allParties.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null)
                return 0;
            if (a.getCreatedAt() == null)
                return 1;
            if (b.getCreatedAt() == null)
                return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return partyMapper.toResponses(allParties);
    }

    private List<Party.PartyStatus> resolveMyHistoryStatuses(String group) {
        String normalizedGroup = group == null || group.isBlank()
                ? "all"
                : group.trim().toLowerCase(Locale.ROOT);

        return switch (normalizedGroup) {
            case "all" -> null;
            case "completed" -> List.of(Party.PartyStatus.COMPLETED, Party.PartyStatus.CHECKED_IN);
            case "ongoing" -> List.of(Party.PartyStatus.PENDING, Party.PartyStatus.MATCHED);
            default -> throw new BadRequestBusinessException(
                    "INVALID_MATE_HISTORY_GROUP",
                    "지원되지 않는 메이트 내역 그룹입니다.");
        };
    }

    private long elapsedNanos(long startedAtNanos) {
        return System.nanoTime() - startedAtNanos;
    }

    /**
     * 사용자 계정 삭제 시 cascade cleanup 처리
     *
     * 호스트인 경우:
     * - 활성(PENDING/MATCHED/SELLING) 파티를 FAILED로 변경
     * - 결제 신청은 환불 재시도를 위해 거절 상태로 보존
     *
     * 참여자인 경우:
     * - 활성 파티의 승인 신청은 currentParticipants 감소 후 호스트에게 알림
     * - 활성 파티의 결제 신청은 환불 재시도를 위해 보존하고 종료 파티 이력은 유지
     *
     * @param userId 삭제될 사용자 ID
     */
    @Transactional
    public void handleUserDeletion(Long userId) {
        log.info("사용자 삭제로 인한 메이트 cascade cleanup 시작: userId={}", userId);

        // 1. 호스트로 생성한 활성 파티 처리
        List<Party.PartyStatus> activeStatuses = List.of(
                Party.PartyStatus.PENDING,
                Party.PartyStatus.MATCHED,
                Party.PartyStatus.SELLING);
        List<Party> hostedParties = partyRepository.findByHostIdAndStatusIn(userId, activeStatuses);
        List<PartyApplication> applicationsSnapshot = applicationRepository.findByApplicantId(userId);

        List<Long> relatedPartyIds = java.util.stream.Stream.concat(
                        hostedParties.stream().map(Party::getId),
                        applicationsSnapshot.stream().map(PartyApplication::getPartyId))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Party> lockedParties = new java.util.HashMap<>();
        for (Long partyId : relatedPartyIds) {
            partyRepository.findByIdForUpdate(partyId)
                    .ifPresent(party -> lockedParties.put(partyId, party));
        }
        List<PartyApplication> applicationsAsParticipant = applicationRepository.findByApplicantId(userId);

        for (Party candidate : hostedParties) {
            Party party = lockedParties.get(candidate.getId());
            if (party == null || !userId.equals(party.getHostId()) || !activeStatuses.contains(party.getStatus())) {
                continue;
            }
            log.info("호스트 파티 취소 처리: partyId={}, status={}", party.getId(), party.getStatus());

            // 파티 상태를 FAILED로 변경
            party.setStatus(Party.PartyStatus.FAILED);
            partyRepository.save(party);

            // 승인된 신청자들은 상태 변경 전에 알림 대상으로 고정
            List<PartyApplication> approvedApplications = applicationRepository
                    .findByPartyIdAndIsApprovedTrue(party.getId());
            List<PartyApplication> paidApplications = applicationRepository.findByPartyId(party.getId()).stream()
                    .filter(application -> Boolean.TRUE.equals(application.getIsPaid()))
                    .sorted(java.util.Comparator.comparing(PartyApplication::getId))
                    .toList();
            for (PartyApplication candidateApplication : paidApplications) {
                PartyApplication application = applicationRepository.findByIdAndApplicantIdForUpdate(
                                candidateApplication.getId(), candidateApplication.getApplicantId())
                        .orElse(null);
                if (application == null || !party.getId().equals(application.getPartyId())) {
                    continue;
                }
                application.setIsApproved(false);
                application.setIsRejected(true);
                application.setRejectedAt(Instant.now());
                applicationRepository.save(application);
            }

            for (PartyApplication application : approvedApplications) {
                try {
                    notificationService.createNotification(
                            application.getApplicantId(),
                            com.example.notification.entity.Notification.NotificationType.PARTY_CANCELLED_HOST_DELETED,
                            "파티가 취소되었습니다",
                            "호스트가 계정을 삭제하여 파티가 자동으로 취소되었습니다. (경기: " +
                                    party.getGameDate() + " " + party.getStadium() + ")",
                            party.getId());
                    log.info("파티 취소 알림 발송: applicantId={}, partyId={}",
                            application.getApplicantId(), party.getId());
                } catch (Exception e) {
                    log.error("파티 취소 알림 발송 실패: applicantId={}, error={}",
                            application.getApplicantId(), e.getMessage());
                }
            }
        }

        // 2. 참여자로 만든 활성 파티 신청 처리
        for (PartyApplication candidate : applicationsAsParticipant.stream()
                .sorted(java.util.Comparator.comparing(PartyApplication::getPartyId)
                        .thenComparing(PartyApplication::getId))
                .toList()) {
            try {
                Long partyId = candidate.getPartyId();
                Long applicationId = candidate.getId();
                Party party = lockedParties.get(partyId);

                if (party == null) {
                    log.warn("파티를 찾을 수 없음: partyId={}", partyId);
                    continue;
                }
                if (userId.equals(party.getHostId()) || !activeStatuses.contains(party.getStatus())) {
                    continue;
                }

                PartyApplication application = applicationRepository
                        .findByIdAndApplicantIdForUpdate(applicationId, userId)
                        .orElse(null);

                if (application == null
                        || !partyId.equals(application.getPartyId())) {
                    continue;
                }

                boolean approved = Boolean.TRUE.equals(application.getIsApproved())
                        && !Boolean.TRUE.equals(application.getIsRejected());
                if (approved) {
                    log.info("참여자 신청 취소 처리: partyId={}, applicantId={}",
                            party.getId(), userId);

                    // currentParticipants 감소
                    if (party.getCurrentParticipants() > 1) {
                        party.setCurrentParticipants(party.getCurrentParticipants() - 1);

                        // MATCHED 상태였다면 다시 PENDING으로 변경
                        if (party.getStatus() == Party.PartyStatus.MATCHED) {
                            party.setStatus(Party.PartyStatus.PENDING);
                        }

                        partyRepository.save(party);
                    }

                    // 호스트에게 알림 발송
                    try {
                        String applicantName = application.getApplicantName() != null ? application.getApplicantName()
                                : "참여자";

                        notificationService.createNotification(
                                party.getHostId(),
                                com.example.notification.entity.Notification.NotificationType.PARTY_PARTICIPANT_LEFT,
                                "참여자가 탈퇴했습니다",
                                applicantName + "님이 계정 삭제로 인해 파티에서 자동 탈퇴되었습니다. (현재 인원: " +
                                        party.getCurrentParticipants() + "/" + party.getMaxParticipants() + ")",
                                party.getId());
                        log.info("참여자 탈퇴 알림 발송: hostId={}, partyId={}",
                                party.getHostId(), party.getId());
                    } catch (Exception e) {
                        log.error("참여자 탈퇴 알림 발송 실패: hostId={}, error={}",
                                party.getHostId(), e.getMessage());
                    }
                }

                preservePaidApplicationForRefundOrDelete(application);

            } catch (RuntimeException e) {
                log.error("참여자 신청 처리 중 오류: applicationId={}, error={}",
                        candidate.getId(), e.getMessage());
                throw e;
            }
        }

        log.info("사용자 삭제로 인한 메이트 cascade cleanup 완료: userId={}, " +
                "취소된 호스트 파티={}, 취소된 참여 신청={}",
                userId, hostedParties.size(), applicationsAsParticipant.size());
    }

    private void preservePaidApplicationForRefundOrDelete(PartyApplication application) {
        if (!Boolean.TRUE.equals(application.getIsPaid())) {
            applicationRepository.delete(application);
            return;
        }
        application.setIsApproved(false);
        application.setIsRejected(true);
        application.setRejectedAt(Instant.now());
        applicationRepository.save(application);
    }

    private String normalizeSearchQuery(String searchQuery) {
        if (searchQuery == null) {
            return "";
        }
        return searchQuery.trim();
    }

    private TicketInfo consumeAndValidateTicketToken(PartyDTO.Request request) {
        String token = request.getVerificationToken();
        if (token == null || token.isBlank()) {
            throw new BadRequestBusinessException("MATE_TICKET_VERIFICATION_REQUIRED", "예매내역 인증이 필요합니다.");
        }

        TicketInfo ticketInfo = ticketVerificationTokenStore.consumeToken(token);
        if (ticketInfo == null) {
            throw new BadRequestBusinessException("MATE_TICKET_VERIFICATION_FAILED", "유효하지 않거나 만료된 예매 인증 정보입니다.");
        }

        if (!validateTicketMatch(ticketInfo, request)) {
            throw new BadRequestBusinessException("MATE_TICKET_VERIFICATION_FAILED", "예매내역 인증 정보가 현재 경기와 일치하지 않습니다.");
        }

        return ticketInfo;
    }

    private boolean validateTicketMatch(TicketInfo ticketInfo, PartyDTO.Request request) {
        if (ticketInfo == null || request == null || request.getGameDate() == null) {
            return false;
        }

        boolean dateMatch = false;
        try {
            if (ticketInfo.getDate() != null && !ticketInfo.getDate().isBlank()) {
                if (ticketInfo.getDate().equals(request.getGameDate().toString())) {
                    dateMatch = true;
                } else {
                    LocalDate ticketDate = LocalDate.parse(ticketInfo.getDate());
                    dateMatch = ticketDate.isEqual(request.getGameDate());
                }
            }
        } catch (Exception e) {
            log.warn("[PartyCreate] Ticket date parsing failed: {}", ticketInfo.getDate());
        }

        if (!dateMatch) {
            return false;
        }

        if (ticketInfo.getStadium() != null && !ticketInfo.getStadium().isBlank()
                && request.getStadium() != null && !request.getStadium().isBlank()) {
            String ticketStadium = ticketInfo.getStadium().trim();
            String requestStadium = request.getStadium().trim();
            boolean stadiumMatch = ticketStadium.equalsIgnoreCase(requestStadium)
                    || ticketStadium.contains(requestStadium)
                    || requestStadium.contains(ticketStadium);
            if (!stadiumMatch) {
                return false;
            }
        }

        String normalizedTicketHome = TicketTeamNormalizer.normalize(ticketInfo.getHomeTeam());
        String normalizedTicketAway = TicketTeamNormalizer.normalize(ticketInfo.getAwayTeam());
        String normalizedRequestHome = TicketTeamNormalizer.normalize(request.getHomeTeam());
        String normalizedRequestAway = TicketTeamNormalizer.normalize(request.getAwayTeam());
        return normalizedTicketHome != null
                && normalizedTicketAway != null
                && normalizedTicketHome.equalsIgnoreCase(normalizedRequestHome)
                && normalizedTicketAway.equalsIgnoreCase(normalizedRequestAway);
    }

    private String normalizeTeamCode(String teamCode) {
        if (teamCode == null) {
            return null;
        }
        String normalized = TeamCodeNormalizer.normalize(teamCode);
        return normalized == null || normalized.isBlank() ? teamCode : normalized;
    }

    private String resolveTeamId(Party.CheeringSide cheeringSide, String homeTeam, String awayTeam) {
        return switch (cheeringSide) {
            case HOME -> homeTeam;
            case AWAY -> awayTeam;
            case NEUTRAL -> "NEUTRAL";
        };
    }

    private String buildSearchText(
            String stadium,
            String homeTeam,
            String awayTeam,
            String section,
            String seatDetail,
            String hostName,
            String description) {
        return String.join(" ",
                normalizedSearchToken(stadium),
                normalizedSearchToken(homeTeam),
                normalizedSearchToken(awayTeam),
                normalizedSearchToken(section),
                normalizedSearchToken(seatDetail),
                normalizedSearchToken(hostName),
                normalizedSearchToken(description)).trim();
    }

    private String normalizedSearchToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Long getUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            throw new AuthenticationRequiredException("인증 정보가 없습니다.");
        }
        String principalName = principal.getName();

        if (principalName == null || principalName.isBlank()) {
            throw new AuthenticationRequiredException("인증 정보가 없습니다.");
        }

        // 1) 신규 인증 구조(일반적으로 userId)를 우선 처리
        try {
            Long principalId = Long.valueOf(principalName);
            return userRepository.findById(principalId)
                    .map(com.example.auth.entity.UserEntity::getId)
                    .orElseGet(() ->
                    // 2) 기존 구조(이메일) 호환 - 숫자로 보이지만 ID가 아닌 경우
                    userRepository.findByEmail(principalName)
                            .map(com.example.auth.entity.UserEntity::getId)
                            .orElseThrow(() -> new InvalidAuthorException("사용자를 찾을 수 없습니다.")));
        } catch (NumberFormatException e) {
            // 2) 기존 구조(이메일) 호환
            return userRepository.findByEmail(principalName)
                    .map(com.example.auth.entity.UserEntity::getId)
                    .orElseThrow(() -> new InvalidAuthorException("사용자를 찾을 수 없습니다."));
        }
    }

    private boolean isSocialVerified(Long userId) {
        return userProviderRepository.findByUserId(userId).stream()
                .anyMatch(provider -> "kakao".equalsIgnoreCase(provider.getProvider())
                        || "naver".equalsIgnoreCase(provider.getProvider()));
    }

    private Party.BadgeType resolveHostBadge(Long userId, boolean socialVerified) {
        int checkedInPartyCount = applicationRepository.countCheckedInPartiesByUserId(userId);
        if (checkedInPartyCount >= 3) {
            return Party.BadgeType.TRUSTED;
        }
        if (socialVerified) {
            return Party.BadgeType.VERIFIED;
        }
        return Party.BadgeType.NEW;
    }

}
