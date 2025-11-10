package com.example.mate.service;

import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;
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

    // 파티 생성
    @Transactional
    public PartyDTO.Response createParty(PartyDTO.Request request) {
        System.out.println("🎫 백엔드 - 받은 Request DTO: " + request);
        System.out.println("🎫 백엔드 - ticketPrice 값: " + request.getTicketPrice());

        Party party = Party.builder()
                .hostId(request.getHostId())
                .hostName(request.getHostName())
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
                

            System.out.println("💾 백엔드 - 저장 전 Entity ticketPrice: " + party.getTicketPrice());
    
            Party savedParty = partyRepository.save(party);
            
            System.out.println("✅ 백엔드 - 저장 후 Entity ticketPrice: " + savedParty.getTicketPrice());
            
            return PartyDTO.Response.from(savedParty);
    }

    // 모든 파티 조회
    @Transactional(readOnly = true)
    public List<PartyDTO.Response> getAllParties() {
        return partyRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PartyDTO.Response::from)
                .collect(Collectors.toList());
    }

    // 파티 ID로 조회
    @Transactional(readOnly = true)
    public PartyDTO.Response getPartyById(Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("파티를 찾을 수 없습니다."));
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
    public PartyDTO.Response updateParty(Long id, PartyDTO.UpdateRequest request) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("파티를 찾을 수 없습니다."));

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
    public PartyDTO.Response incrementParticipants(Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("파티를 찾을 수 없습니다."));

        if (party.getCurrentParticipants() >= party.getMaxParticipants()) {
            throw new RuntimeException("파티가 이미 가득 찼습니다.");
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
    public PartyDTO.Response decrementParticipants(Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("파티를 찾을 수 없습니다."));

        if (party.getCurrentParticipants() <= 1) {
            throw new RuntimeException("호스트는 파티를 떠날 수 없습니다.");
        }

        party.setCurrentParticipants(party.getCurrentParticipants() - 1);
        party.setStatus(Party.PartyStatus.PENDING);

        Party updatedParty = partyRepository.save(party);
        return PartyDTO.Response.from(updatedParty);
    }

    // 파티 삭제
    @Transactional
    public void deleteParty(Long id) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("파티를 찾을 수 없습니다."));
        partyRepository.delete(party);
    }
}