package com.example.mate.controller;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.service.PartyApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyApplicationControllerTest {

    @Mock
    private PartyApplicationService applicationService;

    @InjectMocks
    private PartyApplicationController controller;

    private final Principal principal = () -> "42";

    // ── createApplication ──

    @Test
    @DisplayName("신청 생성 시 201 상태코드를 반환한다")
    void createApplication_returns201() {
        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder().partyId(1L).build();
        PartyApplicationDTO.Response response = PartyApplicationDTO.Response.builder().id(10L).partyId(1L).build();
        when(applicationService.createApplication(request, principal)).thenReturn(response);

        ResponseEntity<?> result = controller.createApplication(request, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    // ── getApplicationsByPartyId ──

    @Test
    @DisplayName("파티별 신청 목록을 조회한다")
    void getApplicationsByPartyId_returnsList() {
        PartyApplicationDTO.Response resp = PartyApplicationDTO.Response.builder().id(1L).build();
        when(applicationService.getApplicationsByPartyId(5L, principal)).thenReturn(List.of(resp));

        ResponseEntity<?> result = controller.getApplicationsByPartyId(5L, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) result.getBody()).hasSize(1);
    }

    // ── getMyApplicationByPartyId ──

    @Test
    @DisplayName("특정 파티에 대한 내 신청을 조회한다")
    void getMyApplicationByPartyId_returnsResponse() {
        PartyApplicationDTO.Response resp = PartyApplicationDTO.Response.builder().id(1L).partyId(5L).build();
        when(applicationService.getMyApplicationByPartyId(5L, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.getMyApplicationByPartyId(5L, principal);

        assertThat(result.getBody()).isEqualTo(resp);
    }

    // ── getMyApplications ──

    @Test
    @DisplayName("내 전체 신청 목록을 조회한다")
    void getMyApplications_returnsList() {
        when(applicationService.getMyApplications(principal)).thenReturn(List.of());

        ResponseEntity<?> result = controller.getMyApplications(principal);

        assertThat((List<?>) result.getBody()).isEmpty();
        verify(applicationService).getMyApplications(principal);
    }

    // ── getPendingApplications ──

    @Test
    @DisplayName("대기 중인 신청 목록을 조회한다")
    void getPendingApplications_returnsList() {
        PartyApplicationDTO.Response resp = PartyApplicationDTO.Response.builder().id(1L).build();
        when(applicationService.getPendingApplications(5L, principal)).thenReturn(List.of(resp));

        ResponseEntity<?> result = controller.getPendingApplications(5L, principal);

        assertThat((List<?>) result.getBody()).hasSize(1);
    }

    // ── getApprovedApplications ──

    @Test
    @DisplayName("승인된 신청 목록을 조회한다")
    void getApprovedApplications_returnsList() {
        when(applicationService.getApprovedApplications(5L, principal)).thenReturn(List.of());

        ResponseEntity<?> result = controller.getApprovedApplications(5L, principal);

        assertThat((List<?>) result.getBody()).isEmpty();
    }

    // ── getRejectedApplications ──

    @Test
    @DisplayName("거절된 신청 목록을 조회한다")
    void getRejectedApplications_returnsList() {
        when(applicationService.getRejectedApplications(5L, principal)).thenReturn(List.of());

        ResponseEntity<?> result = controller.getRejectedApplications(5L, principal);

        assertThat((List<?>) result.getBody()).isEmpty();
    }

    // ── approveApplication ──

    @Test
    @DisplayName("신청을 승인한다")
    void approveApplication_returnsResponse() {
        PartyApplicationDTO.Response resp = PartyApplicationDTO.Response.builder().id(10L).build();
        when(applicationService.approveApplication(10L, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.approveApplication(10L, principal);

        assertThat(result.getBody()).isEqualTo(resp);
        verify(applicationService).approveApplication(10L, principal);
    }

    // ── rejectApplication ──

    @Test
    @DisplayName("신청을 거절한다")
    void rejectApplication_returnsResponse() {
        PartyApplicationDTO.Response resp = PartyApplicationDTO.Response.builder().id(10L).build();
        when(applicationService.rejectApplication(10L, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.rejectApplication(10L, principal);

        assertThat(result.getBody()).isEqualTo(resp);
        verify(applicationService).rejectApplication(10L, principal);
    }

    // ── cancelApplicationWithReason ──

    @Test
    @DisplayName("취소 사유가 있으면 요청 본문을 서비스에 전달한다")
    void cancelApplicationWithReason_withBody_passesRequest() {
        PartyApplicationDTO.CancelRequest cancelReq = PartyApplicationDTO.CancelRequest.builder()
                .cancelMemo("변심").build();
        PartyApplicationDTO.CancelResponse cancelResp = PartyApplicationDTO.CancelResponse.builder()
                .applicationId(10L).build();
        when(applicationService.cancelApplication(eq(10L), eq(principal), eq(cancelReq))).thenReturn(cancelResp);

        ResponseEntity<?> result = controller.cancelApplicationWithReason(10L, cancelReq, principal);

        assertThat(result.getBody()).isEqualTo(cancelResp);
    }

    @Test
    @DisplayName("취소 사유가 없으면 빈 CancelRequest를 생성해 전달한다")
    void cancelApplicationWithReason_withoutBody_usesEmptyRequest() {
        PartyApplicationDTO.CancelResponse cancelResp = PartyApplicationDTO.CancelResponse.builder()
                .applicationId(10L).build();
        when(applicationService.cancelApplication(eq(10L), eq(principal), any(PartyApplicationDTO.CancelRequest.class)))
                .thenReturn(cancelResp);

        ResponseEntity<?> result = controller.cancelApplicationWithReason(10L, null, principal);

        assertThat(result.getBody()).isEqualTo(cancelResp);
    }

    // ── cancelApplication (DELETE) ──

    @Test
    @DisplayName("신청 삭제 시 204를 반환한다")
    void cancelApplication_delete_returns204() {
        ResponseEntity<?> result = controller.cancelApplication(10L, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(applicationService).cancelApplication(10L, principal);
    }
}
