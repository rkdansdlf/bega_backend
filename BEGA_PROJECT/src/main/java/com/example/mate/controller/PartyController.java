package com.example.mate.controller;

import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.service.PartyService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    // 파티 생성
    @PostMapping
    public ResponseEntity<?> createParty(@RequestBody PartyDTO.Request request) {
        try {
            PartyDTO.Response response = partyService.createParty(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (com.example.common.exception.IdentityVerificationRequiredException e) {
            System.err.println("파티 생성 실패 - 본인인증 필요: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("파티 생성 중 오류: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // 모든 파티 조회
    @GetMapping
    public ResponseEntity<Page<PartyDTO.Response>> getAllParties(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String stadium,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<PartyDTO.Response> parties = partyService.getAllParties(teamId, stadium, pageable);
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

    // 내가 참여한 모든 파티 조회 (호스트 + 참여자)
    @GetMapping("/my/{userId}")
    public ResponseEntity<List<PartyDTO.Response>> getMyParties(@PathVariable Long userId) {
        List<PartyDTO.Response> parties = partyService.getMyParties(userId);
        return ResponseEntity.ok(parties);
    }

    // 파티 업데이트
    @PatchMapping("/{id}")
    public ResponseEntity<PartyDTO.Response> updateParty(
            @PathVariable Long id,
            @RequestBody PartyDTO.UpdateRequest request) {
        try {
            PartyDTO.Response response = partyService.updateParty(id, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // 파티 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParty(
            @PathVariable Long id,
            @RequestParam Long hostId) {
        try {
            partyService.deleteParty(id, hostId);
            return ResponseEntity.noContent().build();
        } catch (UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}