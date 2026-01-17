package com.example.mate.service;

import java.util.Optional;
import com.example.auth.service.UserService;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final PartyApplicationRepository applicationRepository;
    private final UserService userService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.storage.buckets.profile}")
    private String profileBucket;

    @Transactional
    @SuppressWarnings("null")
    public PartyDTO.Response createParty(PartyDTO.Request request) {

        // 본인인증(소셜 연동) 여부 확인
        if (!userService.isSocialVerified(request.getHostId())) {
            throw new com.example.common.exception.IdentityVerificationRequiredException(
                    "메이트를 생성하려면 카카오 또는 네이버 계정 연동이 필요합니다.");
        }

        String hostProfileImageUrl = null;
        String hostFavoriteTeam = null;

        try {
            // 사용자 정보에서 favoriteTeam도 가져오기
            var userInfo = userRepository.findById(request.getHostId())
                    .map(user -> {
                        String imageUrl = user.getProfileImageUrl();

                        // 상대 경로를 완전한 URL로 변환
                        if (imageUrl != null && imageUrl.startsWith("/images/")) {
                            String fullUrl = supabaseUrl + "/storage/v1/object/public/" + profileBucket + imageUrl;
                            imageUrl = fullUrl;
                        }

                        // blob URL은 무시
                        if (imageUrl != null && imageUrl.startsWith("blob:")) {
                            imageUrl = null;
                        }
                        String favoriteTeamId = user.getFavoriteTeamId();

                        return new Object[] { imageUrl, favoriteTeamId };
                    })
                    .orElse(new Object[] { null, null });

            hostProfileImageUrl = (String) userInfo[0];
            hostFavoriteTeam = (String) userInfo[1]; // favoriteTeam 저장

        } catch (Exception e) {
            System.out.println("호스트 정보 조회 실패: " + e.getMessage());
        }
        Party party = Party.builder()
                .hostId(request.getHostId())
                .hostName(request.getHostName())
                .hostProfileImageUrl(hostProfileImageUrl) // 변환된 URL 사용
                .hostFavoriteTeam(hostFavoriteTeam)
                .hostBadge(request.getHostBadge() != null ? request.getHostBadge() : Party.BadgeType.NEW)
                .hostRating(request.getHostRating() != null ? request.getHostRating() : 5.0)
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
                .ticketVerified(request.getTicketImageUrl() != null)
                .ticketImageUrl(request.getTicketImageUrl())
                .ticketPrice(request.getTicketPrice())
                .status(Party.PartyStatus.PENDING)
                .build();

        Party savedParty = partyRepository.save(party);

        return PartyDTO.Response.from(savedParty);
    }

    // 모든 파티 조회
    @Transactional(readOnly = true)
    public Page<PartyDTO.Response> getAllParties(String teamId, String stadium, Pageable pageable) {
        Page<Party> parties;

        List<Party.PartyStatus> excludedStatuses = List.of(
                Party.PartyStatus.CHECKED_IN,
                Party.PartyStatus.COMPLETED);

        if (teamId != null && !teamId.isBlank()) {
            if (stadium != null && !stadium.isBlank()) {
                parties = partyRepository.findByTeamIdAndStadiumAndStatusNotInOrderByCreatedAtDesc(
                        teamId, stadium, excludedStatuses, pageable);
            } else {
                parties = partyRepository.findByTeamIdAndStatusNotInOrderByCreatedAtDesc(
                        teamId, excludedStatuses, pageable);
            }
        } else if (stadium != null && !stadium.isBlank()) {
            parties = partyRepository.findByStadiumAndStatusNotInOrderByCreatedAtDesc(
                    stadium, excludedStatuses, pageable);
        } else {
            parties = partyRepository.findByStatusNotInOrderByCreatedAtDesc(
                    excludedStatuses, pageable);
        }

        return parties.map(PartyDTO.Response::from);
    }

    // 파티 ID로 조회
    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public PartyDTO.Response getPartyById(Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));
        return PartyDTO.Response.from(party);
    }

    // 상태별 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getPartiesByStatus(Party.PartyStatus status) {
        return partyRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(PartyDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 호스트별 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getPartiesByHostId(Long hostId) {
        return partyRepository.findByHostId(hostId).stream()
                .map(PartyDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 검색
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> searchParties(String query) {
        return partyRepository.searchParties(query).stream()
                .map(PartyDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 경기 날짜 이후 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getUpcomingParties() {
        LocalDate today = LocalDate.now();
        return partyRepository.findByGameDateAfterOrderByGameDateAsc(today).stream()
                .map(PartyDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 파티 업데이트
    @Transactional
    @SuppressWarnings("null")
    public PartyDTO.Response updateParty(Long id, PartyDTO.UpdateRequest request) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        if (request.getStatus() != null) {
            party.setStatus(request.getStatus());
        }
        if (request.getPrice() != null) {
            party.setPrice(request.getPrice());
        }
        if (request.getDescription() != null) {
            party.setDescription(request.getDescription());
        }

        Party updatedParty = partyRepository.save(party);
        return PartyDTO.Response.from(updatedParty);
    }

    // 파티 참여 인원 증가
    @Transactional
    @SuppressWarnings("null")
    public PartyDTO.Response incrementParticipants(Long id) {
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
        return PartyDTO.Response.from(updatedParty);
    }

    // 파티 참여 인원 감소
    @Transactional
    @SuppressWarnings("null")
    public PartyDTO.Response decrementParticipants(Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        if (party.getCurrentParticipants() <= 1) {
            throw new InvalidApplicationStatusException("호스트는 파티를 떠날 수 없습니다.");
        }

        party.setCurrentParticipants(party.getCurrentParticipants() - 1);
        party.setStatus(Party.PartyStatus.PENDING);

        Party updatedParty = partyRepository.save(party);
        return PartyDTO.Response.from(updatedParty);
    }

    @Transactional
    @SuppressWarnings("null")
    public void deleteParty(Long id, Long hostId) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));

        // 호스트 본인 확인
        if (!party.getHostId().equals(hostId)) {
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
        applicationRepository.deleteAll(
                applicationRepository.findByPartyId(id));

        partyRepository.delete(party);
    }

    // 사용자가 참여한 모든 파티 조회 (호스트 + 참여자)
    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public List<PartyDTO.Response> getMyParties(Long userId) {
        // 1. 호스트로 생성한 파티
        List<Party> hostedParties = partyRepository.findByHostId(userId);

        // 2. 참여자로 승인된 파티
        List<PartyApplication> approvedApplications = applicationRepository.findByApplicantIdAndIsApprovedTrue(userId);

        List<Party> participatedParties = approvedApplications.stream()
                .map(app -> partyRepository.findById(app.getPartyId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // 3. 두 리스트 합치기 (중복 제거)
        List<Party> allParties = new java.util.ArrayList<>(hostedParties);
        participatedParties.forEach(party -> {
            if (allParties.stream().noneMatch(p -> p.getId().equals(party.getId()))) {
                allParties.add(party);
            }
        });

        // 4. 최신순 정렬
        allParties.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        return allParties.stream()
                .map(PartyDTO.Response::from)
                .collect(Collectors.toList());
    }
}