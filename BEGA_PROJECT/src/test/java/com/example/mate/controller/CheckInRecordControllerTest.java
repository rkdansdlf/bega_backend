package com.example.mate.controller;

import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.service.CheckInRecordService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInRecordControllerTest {

    @Mock
    private CheckInRecordService checkInRecordService;

    @InjectMocks
    private CheckInRecordController controller;

    private final Principal principal = () -> "42";

    @Test
    @DisplayName("체크인 시 201을 반환한다")
    void checkIn_returns201() {
        CheckInRecordDTO.Request req = CheckInRecordDTO.Request.builder().partyId(5L).build();
        CheckInRecordDTO.Response resp = CheckInRecordDTO.Response.builder().id(1L).build();
        when(checkInRecordService.checkIn(req, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.checkIn(req, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("QR 세션을 생성한다")
    void createQrSession_returns201() {
        CheckInRecordDTO.QrSessionRequest req = CheckInRecordDTO.QrSessionRequest.builder().partyId(5L).build();
        CheckInRecordDTO.QrSessionResponse resp = CheckInRecordDTO.QrSessionResponse.builder().sessionId("abc").build();
        when(checkInRecordService.createQrSession(req, principal)).thenReturn(resp);

        ResponseEntity<?> result = controller.createQrSession(req, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("파티별 체크인 기록을 조회한다")
    void getCheckInsByPartyId_returnsList() {
        CheckInRecordDTO.Response resp = CheckInRecordDTO.Response.builder().id(1L).build();
        when(checkInRecordService.getCheckInsByPartyId(5L, principal)).thenReturn(List.of(resp));

        ResponseEntity<List<CheckInRecordDTO.Response>> result = controller.getCheckInsByPartyId(5L, principal);

        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("사용자별 체크인 기록을 조회한다")
    void getCheckInsByUserId_returnsList() {
        when(checkInRecordService.getCheckInsByUserId(42L, principal)).thenReturn(List.of());

        ResponseEntity<List<CheckInRecordDTO.Response>> result = controller.getCheckInsByUserId(42L, principal);

        assertThat(result.getBody()).isEmpty();
    }

    @Test
    @DisplayName("파티별 체크인 인원 수를 조회한다")
    void getCheckInCount_returnsCount() {
        when(checkInRecordService.getCheckInCount(5L, principal)).thenReturn(3L);

        ResponseEntity<Long> result = controller.getCheckInCount(5L, principal);

        assertThat(result.getBody()).isEqualTo(3L);
    }

    @Test
    @DisplayName("체크인 여부를 확인한다")
    void isCheckedIn_returnsBoolean() {
        when(checkInRecordService.isCheckedIn(5L, principal)).thenReturn(true);

        ResponseEntity<Boolean> result = controller.isCheckedIn(5L, principal);

        assertThat(result.getBody()).isTrue();
    }
}
