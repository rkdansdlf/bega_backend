package com.example.mate.service;

import java.util.Objects;
import java.util.Optional;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyFullException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.kbo.util.TeamCodeNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PartyService {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final PartyApplicationRepository applicationRepository;
    private final UserProviderRepository userProviderRepository;
    private final com.example.notification.service.NotificationService notificationService;
    private final com.example.profile.storage.service.ProfileImageService profileImageService;

    @Transactional
    public PartyDTO.Response createParty(@NonNull PartyDTO.Request request, Principal principal) {
        Long hostId = getUserIdFromPrincipal(principal);

        // 본인인증(소셜 연동) 여부 확인
        boolean isSocialVerified = userProviderRepository.findByUserId(hostId).stream()
                .anyMatch(p -> "kakao".equalsIgnoreCase(p.getProvider()) || "naver".equalsIgnoreCase(p.getProvider()));
        if (!isSocialVerified) {
            throw new com.example.common.exception.IdentityVerificationRequiredException(
                    "메이트를 생성하려면 카카오 또는 네이버 계정 연동이 필요합니다.");
        }

        // 사용자 정보에서 profileImageUrl, favoriteTeam 가져오기
        var userInfo = userRepository.findById(hostId)
                .map(user -> {
                    String imageUrl = user.getProfileImageUrl();
                    // blob URL 또는 data URL 등은 null로 처리하여 나중에 resolver에서 처리되도록 유도
                    if (imageUrl != null && isLegacyOrInvalidProfileValue(imageUrl)) {
                        imageUrl = null;
                    }
                    return new String[] { imageUrl, user.getFavoriteTeamId() };
                })
                .orElse(new String[] { null, null });

        String hostProfileImageUrl = userInfo[0];
        String hostFavoriteTeam = userInfo[1];

        Party party = Party.builder()
                .hostId(hostId)
                .hostName(request.getHostName())
                .hostProfileImageUrl(hostProfileImageUrl)
                .hostFavoriteTeam(hostFavoriteTeam)
                .hostBadge(Optional.ofNullable(request.getHostBadge()).orElse(Party.BadgeType.NEW))
                .hostRating(Optional.ofNullable(request.getHostRating()).orElse(5.0))
                .teamId(request.getTeamId())
                .gameDate(request.getGameDate())
                .gameTime(request.getGameTime())
                .stadium(request.getStadium())
                .homeTeam(request.getHomeTeam())
                .awayTeam(request.getAwayTeam())
                .section(request.getSection())
                .maxParticipants(request.getMaxParticipants())
                .currentParticipants(1) // 호스트 포함
                .description(request.getDescription())
                .searchText(buildSearchText(
                        request.getStadium(),
                        request.getHomeTeam(),
                        request.getAwayTeam(),
                        request.getSection(),
                        request.getHostName(),
                        request.getDescription()))
                .ticketVerified(request.getTicketImageUrl() != null)
                .ticketImageUrl(request.getTicketImageUrl())
                .ticketPrice(request.getTicketPrice())
                .reservationNumber(request.getReservationNumber())
                .status(Party.PartyStatus.PENDING)
                .build();

        Party savedParty = partyRepository.save(party);

        return convertToDto(savedParty);
    }

    // 모든 파티 조회 (검색 및 필터링 통합)
    @Transactional(readOnly = true)
    public Page<PartyDTO.Response> getAllParties(String teamId, String stadium, LocalDate gameDate, String searchQuery,
            Pageable pageable, Party.PartyStatus status) {
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

        Page<Party> parties = partyRepository.findPartiesWithFilter(
                normalizedTeamId,
                stadium,
                gameDate,
                normalizedSearchQuery,
                excludedStatuses,
                status,
                pageable);

        return parties.map(this::convertToDto);
    }

    // 파티 ID로 조회
    @Transactional(readOnly = true)
    public PartyDTO.Response getPartyById(@NonNull Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));
        return convertToDto(party);
    }

    // 상태별 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getPartiesByStatus(Party.PartyStatus status) {
        return partyRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 호스트별 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getPartiesByHostId(Long hostId) {
        return partyRepository.findByHostId(hostId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 검색
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> searchParties(String query) {
        return partyRepository.searchParties(query).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 경기 날짜 이후 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getUpcomingParties() {
        LocalDate today = LocalDate.now();
        return partyRepository.findByGameDateAfterOrderByGameDateAsc(today).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 파티 업데이트
    @Transactional
    public PartyDTO.Response updateParty(@NonNull Long id, @NonNull PartyDTO.UpdateRequest request,
            Principal principal) {
        Long requesterId = getUserIdFromPrincipal(principal);
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        // 호스트 본인 확인
        if (!party.getHostId().equals(requesterId)) {
            throw new UnauthorizedAccessException("파티 호스트만 수정할 수 있습니다.");
        }

        boolean hasApprovedApplications = applicationRepository.countByPartyIdAndIsApprovedTrue(id) > 0;

        if (request.getStatus() != null) {
            party.setStatus(request.getStatus());
        }
        if (request.getDescription() != null) {
            party.setDescription(request.getDescription());
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
        } else if (request.getPrice() != null || request.getSection() != null || request.getMaxParticipants() != null
                || request.getTicketPrice() != null) {
            throw new InvalidApplicationStatusException(
                    "승인된 참여자가 있거나 모집 중 상태가 아닌 경우 가격/좌석/인원을 변경할 수 없습니다.");
        }

        party.setSearchText(buildSearchText(
                party.getStadium(),
                party.getHomeTeam(),
                party.getAwayTeam(),
                party.getSection(),
                party.getHostName(),
                party.getDescription()));

        Party updatedParty = partyRepository.save(party);
        return convertToDto(updatedParty);
    }

    // 파티 참여 인원 증가
    @Transactional
    public PartyDTO.Response incrementParticipants(@NonNull Long id) {
        Party party = partyRepository.findById(id)
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
        return convertToDto(updatedParty);
    }

    // 파티 참여 인원 감소
    @Transactional
    public PartyDTO.Response decrementParticipants(@NonNull Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        if (party.getCurrentParticipants() <= 1) {
            throw new InvalidApplicationStatusException("호스트는 파티를 떠날 수 없습니다.");
        }

        party.setCurrentParticipants(party.getCurrentParticipants() - 1);
        party.setStatus(Party.PartyStatus.PENDING);

        Party updatedParty = partyRepository.save(party);
        return convertToDto(updatedParty);
    }

    @Transactional
    public void deleteParty(@NonNull Long id, Principal principal) {
        Long requesterId = getUserIdFromPrincipal(principal);
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        // 호스트 본인 확인
        if (!party.getHostId().equals(requesterId)) {
            throw new UnauthorizedAccessException("파티 호스트만 삭제할 수 있습니다.");
        }

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
    public List<PartyDTO.Response> getMyParties(Long userId) {
        if (userId == null) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
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

        return allParties.stream()
                .map(this::convertToDto)
                .toList();
    }

    /**
     * 사용자 계정 삭제 시 cascade cleanup 처리
     *
     * 호스트인 경우:
     * - PENDING 상태 파티는 FAILED로 변경하고 승인된 신청자들에게 알림
     * - MATCHED 상태 파티는 FAILED로 변경하고 모든 참여자에게 알림
     *
     * 참여자인 경우:
     * - 승인된 신청을 취소하고 currentParticipants 감소
     * - 호스트에게 알림 발송
     *
     * @param userId 삭제될 사용자 ID
     */
    @Transactional
    public void handleUserDeletion(Long userId) {
        log.info("사용자 삭제로 인한 메이트 cascade cleanup 시작: userId={}", userId);

        // 1. 호스트로 생성한 PENDING/MATCHED 파티 처리
        List<Party.PartyStatus> activeStatuses = List.of(
                Party.PartyStatus.PENDING,
                Party.PartyStatus.MATCHED);
        List<Party> hostedParties = partyRepository.findByHostIdAndStatusIn(userId, activeStatuses);

        for (Party party : hostedParties) {
            log.info("호스트 파티 취소 처리: partyId={}, status={}", party.getId(), party.getStatus());

            // 파티 상태를 FAILED로 변경
            party.setStatus(Party.PartyStatus.FAILED);
            partyRepository.save(party);

            // 승인된 신청자들에게 알림 발송
            List<PartyApplication> approvedApplications = applicationRepository
                    .findByPartyIdAndIsApprovedTrue(party.getId());

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

        // 2. 참여자로 승인된 신청 처리
        List<PartyApplication> approvedApplicationsAsParticipant = applicationRepository
                .findByApplicantIdAndIsApprovedTrueAndIsRejectedFalse(userId);

        for (PartyApplication application : approvedApplicationsAsParticipant) {
            try {
                Party party = partyRepository.findById(application.getPartyId())
                        .orElse(null);

                if (party == null) {
                    log.warn("파티를 찾을 수 없음: partyId={}", application.getPartyId());
                    applicationRepository.delete(application);
                    continue;
                }

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

                // 신청 삭제
                applicationRepository.delete(application);

            } catch (Exception e) {
                log.error("참여자 신청 처리 중 오류: applicationId={}, error={}",
                        application.getId(), e.getMessage());
            }
        }

        log.info("사용자 삭제로 인한 메이트 cascade cleanup 완료: userId={}, " +
                "취소된 호스트 파티={}, 취소된 참여 신청={}",
                userId, hostedParties.size(), approvedApplicationsAsParticipant.size());
    }

    private PartyDTO.Response convertToDto(Party party) {
        if (party == null)
            return null;

        PartyDTO.Response response = PartyDTO.Response.from(party);
        String profilePathOrUrl = party.getHostProfileImageUrl();

        // 파티엔 있는데 만약 무효한 값이거나 비어있다면 DB에서 다시 조회 (fallback)
        if (isLegacyOrInvalidProfileValue(profilePathOrUrl)) {
            profilePathOrUrl = userRepository.findProfileImageUrlById(party.getHostId())
                    .filter(this::isUsableProfileValue)
                    .orElse(null);
        }

        // 호스트의 최종 결정된 profilePathOrUrl이 path 형태인 경우 최종적으로 URL로 변환
        String resolvedUrl = profileImageService.getProfileImageUrl(profilePathOrUrl);
        response.setHostProfileImageUrl(resolvedUrl);

        return response;
    }

    private String normalizeSearchQuery(String searchQuery) {
        if (searchQuery == null) {
            return "";
        }
        return searchQuery.trim();
    }

    private String buildSearchText(
            String stadium,
            String homeTeam,
            String awayTeam,
            String section,
            String hostName,
            String description) {
        return String.join(" ",
                normalizedSearchToken(stadium),
                normalizedSearchToken(homeTeam),
                normalizedSearchToken(awayTeam),
                normalizedSearchToken(section),
                normalizedSearchToken(hostName),
                normalizedSearchToken(description)).trim();
    }

    private String normalizedSearchToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isLegacyOrInvalidProfileValue(String profilePathOrUrl) {
        if (profilePathOrUrl == null || profilePathOrUrl.isBlank()) {
            return false;
        }
        return profilePathOrUrl.startsWith("/assets/")
                || profilePathOrUrl.startsWith("/src/assets/")
                || profilePathOrUrl.startsWith("blob:")
                || profilePathOrUrl.startsWith("data:");
    }

    private boolean isUsableProfileValue(String profilePathOrUrl) {
        if (profilePathOrUrl == null || profilePathOrUrl.isBlank()) {
            return false;
        }
        return !isLegacyOrInvalidProfileValue(profilePathOrUrl);
    }

    private Long getUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
        }
        String principalName = principal.getName();

        if (principalName == null || principalName.isBlank()) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
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
                            .orElseThrow(() -> new UnauthorizedAccessException("사용자를 찾을 수 없습니다.")));
        } catch (NumberFormatException e) {
            // 2) 기존 구조(이메일) 호환
            return userRepository.findByEmail(principalName)
                    .map(com.example.auth.entity.UserEntity::getId)
                    .orElseThrow(() -> new UnauthorizedAccessException("사용자를 찾을 수 없습니다."));
        }
    }
}
