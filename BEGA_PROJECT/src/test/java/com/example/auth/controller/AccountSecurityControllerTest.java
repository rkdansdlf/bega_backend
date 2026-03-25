package com.example.auth.controller;

import com.example.auth.dto.AccountDeletionRecoveryInfoDto;
import com.example.auth.dto.AccountDeletionRecoveryRequestDto;
import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountSecurityService;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityControllerTest {

    @Mock
    private AccountSecurityService accountSecurityService;

    @Mock
    private AccountDeletionService accountDeletionService;

    @InjectMocks
    private AccountSecurityController controller;

    @Test
    @DisplayName("보안 이벤트를 조회한다")
    void getSecurityEvents_returnsSuccess() {
        when(accountSecurityService.getSecurityEvents(42L)).thenReturn(List.of());

        ResponseEntity<ApiResponse> result = controller.getSecurityEvents(42L);

        assertThat(result.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("userId가 null이면 인증 예외를 던진다")
    void getSecurityEvents_nullUserId_throwsAuthException() {
        assertThatThrownBy(() -> controller.getSecurityEvents(null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("신뢰 기기 목록을 조회한다")
    void getTrustedDevices_returnsSuccess() {
        when(accountSecurityService.getTrustedDevices(42L)).thenReturn(List.of());

        ResponseEntity<ApiResponse> result = controller.getTrustedDevices(42L);

        assertThat(result.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("신뢰 기기를 해제한다")
    void deleteTrustedDevice_returnsSuccess() {
        ResponseEntity<ApiResponse> result = controller.deleteTrustedDevice(42L, 1L);

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(accountSecurityService).revokeTrustedDevice(42L, 1L);
    }

    @Test
    @DisplayName("삭제 복구 정보를 조회한다")
    void getDeletionRecoveryInfo_returnsSuccess() {
        AccountDeletionRecoveryInfoDto info = AccountDeletionRecoveryInfoDto.builder().build();
        when(accountDeletionService.getRecoveryInfo("token123")).thenReturn(info);

        ResponseEntity<ApiResponse> result = controller.getDeletionRecoveryInfo("token123");

        assertThat(result.getBody().getData()).isEqualTo(info);
    }

    @Test
    @DisplayName("계정 삭제를 복구한다")
    void recoverDeletedAccount_returnsSuccess() {
        AccountDeletionRecoveryRequestDto req = new AccountDeletionRecoveryRequestDto();
        req.setToken("token123");

        ResponseEntity<ApiResponse> result = controller.recoverDeletedAccount(req);

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(accountDeletionService).recoverAccount("token123");
    }
}
