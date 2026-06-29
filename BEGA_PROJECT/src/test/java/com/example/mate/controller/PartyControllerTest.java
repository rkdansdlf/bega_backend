package com.example.mate.controller;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.exception.InvalidPartyStatusException;
import com.example.mate.service.PartyFavoriteService;
import com.example.mate.service.PartyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PartyControllerTest {

    @Mock
    private PartyService partyService;

    @Mock
    private PartyFavoriteService partyFavoriteService;

    @InjectMocks
    private PartyController partyController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(partyController)
                .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
                .build();
    }

    // ── createParty ──

    @Test
    @DisplayName("파티 생성 시 201 상태코드와 응답을 반환한다")
    void createParty_returns201WithResponse() {
        PartyDTO.Request request = PartyDTO.Request.builder().teamId("KIA").build();
        PartyDTO.Response response = PartyDTO.Response.builder().id(1L).teamId("KIA").build();
        when(partyService.createParty(request, 42L)).thenReturn(response);

        ResponseEntity<?> result = partyController.createParty(request, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
        verify(partyService).createParty(request, 42L);
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

    @Test
    @DisplayName("허용되지 않은 정렬 필드는 createdAt으로 대체하고 page size는 30으로 제한한다")
    void getAllParties_rejectsUnsupportedSortAndCapsPageSize() {
        Page<PartyDTO.PublicResponse> page = new PageImpl<>(List.of());
        when(partyService.getAllParties(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class), isNull(), isNull()))
                .thenReturn(page);

        partyController.getAllParties(null, null, null, null, null, 0, 100, "description", "desc", null);

        verify(partyService).getAllParties(isNull(), isNull(), isNull(), isNull(),
                argThat(p -> p.getPageSize() == 30
                        && p.getSort().getOrderFor("createdAt") != null
                        && p.getSort().getOrderFor("description") == null),
                isNull(), isNull());
    }

    @Test
    @DisplayName("인증 사용자의 파티 목록 JSON은 favorited 상태를 포함한다")
    void getAllParties_mockMvcAuthenticated_preservesFavoritedState() throws Exception {
        PartyDTO.PublicResponse favorited = PartyDTO.PublicResponse.builder()
                .id(1L)
                .favorited(true)
                .seatDetail("305블록 12열")
                .build();
        PartyDTO.PublicResponse notFavorited = PartyDTO.PublicResponse.builder()
                .id(2L)
                .favorited(false)
                .build();
        Page<PartyDTO.PublicResponse> page = new PageImpl<>(
                List.of(favorited, notFavorited),
                PageRequest.of(0, 9),
                2);
        when(partyService.getAllParties(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class), isNull(), eq(99L)))
                .thenReturn(page);

        mockMvc.perform(get("/api/parties")
                        .principal(new TestingAuthenticationToken(99L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].favorited").value(true))
                .andExpect(jsonPath("$.content[0].seatDetail").value("305블록 12열"))
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].favorited").value(false));

        verify(partyService).getAllParties(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class), isNull(), eq(99L));
    }

    @Test
    @DisplayName("익명 파티 목록 JSON은 favorited=false를 포함하고 userId를 null로 전달한다")
    void getAllParties_mockMvcAnonymous_preservesFalseFavoriteState() throws Exception {
        PartyDTO.PublicResponse notFavorited = PartyDTO.PublicResponse.builder()
                .id(1L)
                .favorited(false)
                .build();
        Page<PartyDTO.PublicResponse> page = new PageImpl<>(
                List.of(notFavorited),
                PageRequest.of(0, 9),
                1);
        when(partyService.getAllParties(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/api/parties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].favorited").value(false));

        verify(partyService).getAllParties(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class), isNull(), isNull());
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

    // ── 찜(favorite) ──

    @Test
    @DisplayName("찜 추가 시 favorited=true를 반환하고 서비스를 호출한다")
    void addFavorite_returnsTrueAndCallsService() {
        ResponseEntity<Map<String, Boolean>> result = partyController.addFavorite(5L, 99L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("favorited", true));
        verify(partyFavoriteService).addFavorite(99L, 5L);
    }

    @Test
    @DisplayName("찜 추가는 인증 사용자를 요구한다")
    void addFavorite_nullUser_throwsAuthException() {
        assertThatThrownBy(() -> partyController.addFavorite(5L, null))
                .isInstanceOf(AuthenticationRequiredException.class);
        verify(partyFavoriteService, never()).addFavorite(any(), any());
    }

    @Test
    @DisplayName("찜 삭제 시 favorited=false를 반환하고 서비스를 호출한다")
    void removeFavorite_returnsFalseAndCallsService() {
        ResponseEntity<Map<String, Boolean>> result = partyController.removeFavorite(5L, 99L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("favorited", false));
        verify(partyFavoriteService).removeFavorite(99L, 5L);
    }

    @Test
    @DisplayName("찜 삭제는 인증 사용자를 요구한다")
    void removeFavorite_nullUser_throwsAuthException() {
        assertThatThrownBy(() -> partyController.removeFavorite(5L, null))
                .isInstanceOf(AuthenticationRequiredException.class);
        verify(partyFavoriteService, never()).removeFavorite(any(), any());
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
    @DisplayName("내 메이트 내역 조회 시 페이징 조건으로 서비스를 호출한다")
    void getMyPartyHistory_returnsPagedResponse() {
        PartyDTO.HistoryResponse resp = PartyDTO.HistoryResponse.builder().id(1L).build();
        Page<PartyDTO.HistoryResponse> page = new PageImpl<>(List.of(resp), PageRequest.of(0, 20), 1);
        when(partyService.getMyPartyHistory(eq(42L), eq("all"), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<PartyDTO.HistoryResponse>> result =
                partyController.getMyPartyHistory("all", 0, 20, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getContent()).hasSize(1);
        verify(partyService).getMyPartyHistory(
                eq(42L),
                eq("all"),
                argThat(p -> p.getPageNumber() == 0
                        && p.getPageSize() == 20
                        && p.getSort().getOrderFor("createdAt").getDirection() == Sort.Direction.DESC
                        && p.getSort().getOrderFor("id").getDirection() == Sort.Direction.DESC));
    }

    @Test
    @DisplayName("내 메이트 내역 조회는 page를 0 이상, size를 최대 50으로 제한한다")
    void getMyPartyHistory_clampsPageAndSize() {
        Page<PartyDTO.HistoryResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(partyService.getMyPartyHistory(eq(42L), eq("ongoing"), any(Pageable.class))).thenReturn(page);

        partyController.getMyPartyHistory("ongoing", -3, 999, 42L);

        verify(partyService).getMyPartyHistory(
                eq(42L),
                eq("ongoing"),
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 50));
    }

    @Test
    @DisplayName("내 메이트 내역 조회 시 userId가 null이면 AuthenticationRequiredException을 던진다")
    void getMyPartyHistory_nullUserId_throwsAuthException() {
        assertThatThrownBy(() -> partyController.getMyPartyHistory("all", 0, 20, null))
                .isInstanceOf(AuthenticationRequiredException.class);
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
        when(partyService.updateParty(1L, request, 42L)).thenReturn(response);

        ResponseEntity<?> result = partyController.updateParty(1L, request, 42L);

        assertThat(result.getBody()).isEqualTo(response);
        verify(partyService).updateParty(1L, request, 42L);
    }

    // ── deleteParty ──

    @Test
    @DisplayName("파티 삭제 시 204 상태코드를 반환한다")
    void deleteParty_returns204NoContent() {
        ResponseEntity<?> result = partyController.deleteParty(1L, 42L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(partyService).deleteParty(1L, 42L);
    }

    private static final class TestAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            Principal principal = webRequest.getUserPrincipal();
            if (principal instanceof Authentication authentication) {
                return authentication.getPrincipal();
            }
            return null;
        }
    }
}
