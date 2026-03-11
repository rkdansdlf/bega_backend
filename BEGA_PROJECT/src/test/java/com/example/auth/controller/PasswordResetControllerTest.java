package com.example.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.auth.dto.PasswordResetRequestDto;
import com.example.auth.service.PasswordResetService;
import com.example.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock
    private PasswordResetService passwordResetService;

    private PasswordResetController controller;

    @BeforeEach
    void setUp() {
        controller = new PasswordResetController(passwordResetService);
    }

    @Test
    void requestPasswordReset_returnsGenericSuccessMessage() {
        PasswordResetRequestDto request = new PasswordResetRequestDto("user@example.com");

        ResponseEntity<ApiResponse> response = controller.requestPasswordReset(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("입력한 이메일로 가입된 계정이 있다면 비밀번호 재설정 안내를 발송했습니다.");
        verify(passwordResetService).requestPasswordReset(request);
    }
}
