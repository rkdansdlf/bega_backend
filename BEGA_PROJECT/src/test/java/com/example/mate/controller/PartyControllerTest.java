package com.example.mate.controller;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.exception.InvalidPartyStatusException;
import com.example.mate.service.PartyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyControllerTest {

    @Mock
    private PartyService partyService;

    @InjectMocks
    private PartyController partyController;

    private final Principal principal = () -> "42";

    // ── createParty ──

    @Test
    @DisplayName("파티 생성 시 201 상태코드와 응답을 반환한다")
    void createParty_returns201WithResponse() {
        PartyDTO.Request request = PartyDTO.Request.builder().teamId("KIA").build();
        PartyDTO.Response response = PartyDTO.Response.builder().id(1L).teamId("KIA").build();
        when(partyService.createParty(request, principal)).thenReturn(response);

        ResponseEntity<?> result = partyController.createParty(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
        verify(partyService).createParty(request, principal);
    }

    // ── getAllParties ──

    @Test
    @DisplayName("필터와 페이지네이션으로 파티 목록을 조회한다")
    void getAllParties_withFilters_callsServiceCorrectly() {
        PartyDTO.PublicResponse pub = PartyDTO.PublicResponse.builder().id(1L).build();
        Page<PartyDTO.PublicResponse> page = new PageImpl<>(List.of(pub));
        when(partyService.getAllParties(eq("KIA"), eq("JAMSIL"), any(LocalDate.class),
                eq("test"), any(Pageable.class), eq(Party.PartyStatus.PENDING), eq(99L)))
                .thenReturn(page);

        ResponseEntity<Page<PartyDTO.PublicResponse>> result = partyController.getAllParties(
                "KIA", "JAMSIL", LocalDate.of(2026, 3, 1), "test",
                "PENDING", 0, 9, "createdAt", "desc", 99L);

        assertThat(result.getBody().getContent()).hasSize(1);
        verify(partyService).getAllParties(eq("KIA"), eq("JAMSIL"), any(LocalDate.class),
                eq("test"), any(Pageable.class), eq(Party.PartyStatus.PENDING), eq(99L));
    }

    @Test
    @DisplayName("status가 null이면 parsedStatus를 null로 전달한다")
    void getAllParties_withNullStatus_passesNullToService() {
        Page<PartyDTO.PublicResponse> page = new PageImpl<>(List.of());
        when(partyService.getAllParties(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class), isNull(), isNull()))
                .thenReturn(page);

        ResponseEntity<Page<PartyDTO.PublicResponse>> result = partyController.getAllParties(
                null, null, null, null, null, 0, 9, "createdAt", "desc", null);

        assertThat(result.getBody().getContent()).isEmpty();
    }

    @Test
    @DisplayName("유효하지 않은 status 문자열이면 InvalidPartyStatusException을 던진다")
    void getAllParties_invalidStatus_throwsException() {
        assertThatThrownBy(() -> partyController.getAllParties(
                null, null, null, null, "INVALID_STATUS", 0, 9, "createdAt", "desc", null))
                .isInstanceOf(InvalidPartyStatusException.class);
    }

    @Test
    @DisplayName("sortDir이 asc이면 ASC 정렬을 적용한다")
    void getAllParties_sortDirectionAsc() {
        Page<PartyDTO.PublicResponse> page = new PageImpl<>(List.of());
        when(partyService.getAllParties(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class), isNull(), isNull()))
                .thenReturn(page);

        partyController.getAllParties(null, null, null, null, null, 0, 9, "createdAt", "asc", null);

        verify(partyService).getAllParties(isNull(), isNull(), isNull(), isNull(),
                argThat(p -> p.getSort().getOrderFor("createdAt").getDirection() == Sort.Direction.ASC),
                isNull(), isNull());
    }

    // ── getPartyById ──

    @Test
    @DisplayName("파티 ID로 조회 시 응답을 반환한다")
    void getPartyById_returnsResponse() {
        PartyDTO.PublicResponse pub = PartyDTO.PublicResponse.builder().id(5L).build();
        when(partyService.getPartyById(5L, 99L)).thenReturn(pub);

        ResponseEntity<PartyDTO.PublicResponse> result = partyController.getPartyById(5L, 99L);

        assertThat(result.getBody()).isEqualTo(pub);
        verify(partyService).getPartyById(5L, 99L);
    }

    // ── getPartiesByStatus ──

    @Test
    @DisplayName("유효한 상태로 파티 목록을 조회한다")
    void getPartiesByStatus_returnsListForValidStatus() {
        PartyDTO.PublicResponse pub = PartyDTO.PublicResponse.builder().id(1L).build();
        when(partyService.getPartiesByStatus(Party.PartyStatus.PENDING, 99L))
                .thenReturn(List.of(pub));

        ResponseEntity<List<PartyDTO.PublicResponse>> result =
                partyController.getPartiesByStatus("PENDING", 99L);

        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("유효하지 않은 상태 문자열이면 예외를 던진다")
    void getPartiesByStatus_invalidStatus_throwsException() {
        assertThatThrownBy(() -> partyController.getPartiesByStatus("INVALID", null))
                .isInstanceOf(InvalidPartyStatusException.class);
    }

    // ── getPartiesByHostHandle ──

    @Test
    @DisplayName("호스트 핸들로 파티를 조회한다")
    void getPartiesByHostHandle_returnsResponse() {
        PartyDTO.PublicResponse pub = PartyDTO.PublicResponse.builder().id(1L).build();
        when(partyService.getPartiesByHostHandle("testuser", 99L)).thenReturn(List.of(pub));

        ResponseEntity<List<PartyDTO.PublicResponse>> result =
                partyController.getPartiesByHostHandle("testuser", 99L);

        assertThat(result.getBody()).hasSize(1);
        verify(partyService).getPartiesByHostHandle("testuser", 99L);
    }

    // ── searchParties ──

    @Test
    @DisplayName("검색어로 파티를 조회한다")
    void searchParties_returnsResults() {
        PartyDTO.PublicResponse pub = PartyDTO.PublicResponse.builder().id(1L).build();
        when(partyService.searchParties("KIA", 99L)).thenReturn(List.of(pub));

        ResponseEntity<List<PartyDTO.PublicResponse>> result =
                partyController.searchParties("KIA", 99L);

        assertThat(result.getBody()).hasSize(1);
        verify(partyService).searchParties("KIA", 99L);
    }

    // ── getUpcomingParties ──

    @Test
    @DisplayName("다가오는 파티 목록을 조회한다")
    void getUpcomingParties_returnsResults() {
        when(partyService.getUpcomingParties(99L)).thenReturn(List.of());

        ResponseEntity<List<PartyDTO.PublicResponse>> result =
                partyController.getUpcomingParties(99L);

        assertThat(result.getBody()).isEmpty();
        verify(partyService).getUpcomingParties(99L);
    }

    // ── getMyParties ──

    @Test
    @DisplayName("내 파티 조회 시 인증된 userId로 서비스를 호출한다")
    void getMyParties_returnsResponse() {
        PartyDTO.Response resp = PartyDTO.Response.builder().id(1L).build();
        when(partyService.getMyParties(42L)).thenReturn(List.of(resp));

        ResponseEntity<?> result = partyController.getMyParties(42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(partyService).getMyParties(42L);
    }

    @Test
    @DisplayName("userId가 null이면 AuthenticationRequiredException을 던진다")
    void getMyParties_nullUserId_throwsAuthException() {
        assertThatThrownBy(() -> partyController.getMyParties(null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    // ── updateParty ──

    @Test
    @DisplayName("파티 업데이트 시 응답을 반환한다")
    void updateParty_returnsUpdatedResponse() {
        PartyDTO.UpdateRequest request = PartyDTO.UpdateRequest.builder().description("updated").build();
        PartyDTO.Response response = PartyDTO.Response.builder().id(1L).description("updated").build();
        when(partyService.updateParty(1L, request, principal)).thenReturn(response);

        ResponseEntity<?> result = partyController.updateParty(1L, request, principal);

        assertThat(result.getBody()).isEqualTo(response);
        verify(partyService).updateParty(1L, request, principal);
    }

    // ── deleteParty ──

    @Test
    @DisplayName("파티 삭제 시 204 상태코드를 반환한다")
    void deleteParty_returns204NoContent() {
        ResponseEntity<?> result = partyController.deleteParty(1L, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(partyService).deleteParty(1L, principal);
    }
}
