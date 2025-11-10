package com.example.mate.controller;

import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.service.PartyService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<PartyDTO.Response> createParty(@RequestBody PartyDTO.Request request) {
        try {
            PartyDTO.Response response = partyService.createParty(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // 모든 파티 조회
    @GetMapping
    public ResponseEntity<List<PartyDTO.Response>> getAllParties() {
        List<PartyDTO.Response> parties = partyService.getAllParties();
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
    public ResponseEntity<Void> deleteParty(@PathVariable Long id) {
        try {
            partyService.deleteParty(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}