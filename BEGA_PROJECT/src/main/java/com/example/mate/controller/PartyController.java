package com.example.mate.controller;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.exception.InvalidPartyStatusException;
import com.example.mate.service.PartyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Tag(name = "파티 매칭", description = "파티 생성, 조회, 신청, 체크인, 실시간 WebSocket 채팅")
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private static final int MAX_PAGE_SIZE = 30;
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 20;
    private static final int MAX_HISTORY_PAGE_SIZE = 50;
    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "gameDate", "currentParticipants");

    private final PartyService partyService;

    // 파티 생성
    @PostMapping
    public ResponseEntity<PartyDTO.Response> createParty(
            @Valid @RequestBody PartyDTO.Request request,
            @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        PartyDTO.Response response = partyService.createParty(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 모든 파티 조회
    @GetMapping
    public ResponseEntity<Page<PartyDTO.PublicResponse>> getAllParties(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String stadium,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal Long currentUserId) {
        Party.PartyStatus parsedStatus = parsePartyStatus(status);

        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        String resolvedSortBy = sortBy != null && ALLOWED_SORTS.contains(sortBy) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(direction, resolvedSortBy));
        Page<PartyDTO.PublicResponse> parties = partyService.getAllParties(
                teamId,
                stadium,
                date,
                searchQuery,
                pageable,
                parsedStatus,
                currentUserId);
        return ResponseEntity.ok(parties);
    }

    // 파티 ID로 조회
    @GetMapping("/{id}")
    public ResponseEntity<PartyDTO.PublicResponse> getPartyById(
            @PathVariable Long id,
            @AuthenticationPrincipal Long currentUserId) {
        PartyDTO.PublicResponse response = partyService.getPartyById(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    // 상태별 파티 조회
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PartyDTO.PublicResponse>> getPartiesByStatus(
            @PathVariable String status,
            @AuthenticationPrincipal Long currentUserId) {
        Party.PartyStatus partyStatus = requirePartyStatus(status);
        List<PartyDTO.PublicResponse> parties = partyService.getPartiesByStatus(partyStatus, currentUserId);
        return ResponseEntity.ok(parties);
    }

    // 호스트별 파티 조회
    @GetMapping("/profile/{handle}")
    public ResponseEntity<List<PartyDTO.PublicResponse>> getPartiesByHostHandle(
            @PathVariable String handle,
            @AuthenticationPrincipal Long currentUserId) {
        List<PartyDTO.PublicResponse> parties = partyService.getPartiesByHostHandle(handle, currentUserId);
        return ResponseEntity.ok(parties);
    }

    // 검색
    @GetMapping("/search")
    public ResponseEntity<List<PartyDTO.PublicResponse>> searchParties(
            @RequestParam String query,
            @AuthenticationPrincipal Long currentUserId) {
        List<PartyDTO.PublicResponse> parties = partyService.searchParties(query, currentUserId);
        return ResponseEntity.ok(parties);
    }

    // 경기 예정 파티 조회
    @GetMapping("/upcoming")
    public ResponseEntity<List<PartyDTO.PublicResponse>> getUpcomingParties(
            @AuthenticationPrincipal Long currentUserId) {
        List<PartyDTO.PublicResponse> parties = partyService.getUpcomingParties(currentUserId);
        return ResponseEntity.ok(parties);
    }

    // 마이페이지 메이트 내역 조회 (호스트 + 승인된 참여자, 페이징)
    @GetMapping("/my/history")
    public ResponseEntity<Page<PartyDTO.HistoryResponse>> getMyPartyHistory(
            @RequestParam(defaultValue = "all") String group,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_HISTORY_PAGE_SIZE : Math.min(size, MAX_HISTORY_PAGE_SIZE);
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        Page<PartyDTO.HistoryResponse> parties = partyService.getMyPartyHistory(userId, group, pageable);
        return ResponseEntity.ok(parties);
    }

    // 내가 참여한 모든 파티 조회 (호스트 + 참여자) - Principal 기반
    @GetMapping("/my")
    public ResponseEntity<List<PartyDTO.Response>> getMyParties(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }

        List<PartyDTO.Response> parties = partyService.getMyParties(userId);
        return ResponseEntity.ok(parties);
    }

    // 파티 업데이트
    @PatchMapping("/{id}")
    public ResponseEntity<PartyDTO.Response> updateParty(
            @PathVariable Long id,
            @Valid @RequestBody PartyDTO.UpdateRequest request,
            @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        PartyDTO.Response response = partyService.updateParty(id, request, userId);
        return ResponseEntity.ok(response);
    }

    // 파티 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParty(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        partyService.deleteParty(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Party.PartyStatus parsePartyStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        return requirePartyStatus(status);
    }

    private Party.PartyStatus requirePartyStatus(String status) {
        return Arrays.stream(Party.PartyStatus.values())
                .filter(candidate -> candidate.name().equalsIgnoreCase(status.trim()))
                .findFirst()
                .orElseThrow(() -> new InvalidPartyStatusException(status));
    }
}
