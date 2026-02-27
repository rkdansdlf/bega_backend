package com.example.mate.controller;

import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.service.PartyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "파티 매칭", description = "파티 생성, 조회, 신청, 체크인, 실시간 WebSocket 채팅")
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    // 파티 생성
    @PostMapping
    public ResponseEntity<?> createParty(@RequestBody PartyDTO.Request request, java.security.Principal principal) {
        try {
            PartyDTO.Response response = partyService.createParty(request, principal);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (com.example.common.exception.IdentityVerificationRequiredException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 모든 파티 조회
    @GetMapping
    public ResponseEntity<Page<PartyDTO.Response>> getAllParties(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String stadium,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Party.PartyStatus parsedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                parsedStatus = Party.PartyStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
        }

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<PartyDTO.Response> parties = partyService.getAllParties(teamId, stadium, date, searchQuery, pageable, parsedStatus);
        return ResponseEntity.ok(parties);
    }

    // 파티 ID로 조회
    @GetMapping("/{id}")
    public ResponseEntity<PartyDTO.Response> getPartyById(@PathVariable Long id) {
        try {
            PartyDTO.Response response = partyService.getPartyById(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // 상태별 파티 조회
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PartyDTO.Response>> getPartiesByStatus(@PathVariable String status) {
        try {
            Party.PartyStatus partyStatus = Party.PartyStatus.valueOf(status.toUpperCase());
            List<PartyDTO.Response> parties = partyService.getPartiesByStatus(partyStatus);
            return ResponseEntity.ok(parties);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // 호스트별 파티 조회
    @GetMapping("/host/{hostId}")
    public ResponseEntity<List<PartyDTO.Response>> getPartiesByHostId(@PathVariable Long hostId) {
        List<PartyDTO.Response> parties = partyService.getPartiesByHostId(hostId);
        return ResponseEntity.ok(parties);
    }

    // 검색
    @GetMapping("/search")
    public ResponseEntity<List<PartyDTO.Response>> searchParties(@RequestParam String query) {
        List<PartyDTO.Response> parties = partyService.searchParties(query);
        return ResponseEntity.ok(parties);
    }

    // 경기 예정 파티 조회
    @GetMapping("/upcoming")
    public ResponseEntity<List<PartyDTO.Response>> getUpcomingParties() {
        List<PartyDTO.Response> parties = partyService.getUpcomingParties();
        return ResponseEntity.ok(parties);
    }

    // 내가 참여한 모든 파티 조회 (호스트 + 참여자) - Principal 기반
    @GetMapping("/my")
    public ResponseEntity<List<PartyDTO.Response>> getMyParties(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<PartyDTO.Response> parties = partyService.getMyParties(userId);
        return ResponseEntity.ok(parties);
    }

    // 파티 업데이트
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateParty(
            @PathVariable Long id,
            @RequestBody PartyDTO.UpdateRequest request,
            java.security.Principal principal) {
        try {
            PartyDTO.Response response = partyService.updateParty(id, request, principal);
            return ResponseEntity.ok(response);
        } catch (UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 파티 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteParty(
            @PathVariable Long id,
            java.security.Principal principal) {
        try {
            partyService.deleteParty(id, principal);
            return ResponseEntity.noContent().build();
        } catch (UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
