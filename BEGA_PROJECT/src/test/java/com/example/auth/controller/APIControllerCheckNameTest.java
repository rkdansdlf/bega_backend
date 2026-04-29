package com.example.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.dto.AvailabilityCheckResponseDto;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.service.AuthRegistrationService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.service.UserService;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.DuplicateNameException;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.web.ClientIpResolver;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class APIControllerCheckNameTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthRegistrationService authRegistrationService;

    @Mock
    private PolicyConsentService policyConsentService;

    @Mock
    private OAuth2StateService oAuth2StateService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private ClientIpResolver clientIpResolver;

    @InjectMocks
    private APIController apiController;

    @Test
    @DisplayName("사용 가능한 닉네임이면 success 응답을 반환한다")
    void checkName_returnsSuccessWhenAvailable() {
        when(userService.ensureNameAvailable(1L, " new-name ")).thenReturn("new-name");

        ResponseEntity<ApiResponse> response = apiController.checkName(1L, " new-name ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("사용 가능한 닉네임입니다.");
        assertThat(response.getBody().getData()).isEqualTo(Map.of("available", true, "normalized", "new-name"));
    }

    @Test
    @DisplayName("중복 닉네임이면 conflict 예외를 전파한다")
    void checkName_propagatesDuplicateNameException() {
        when(userService.ensureNameAvailable(1L, "taken"))
                .thenThrow(new DuplicateNameException("taken"));

        assertThatThrownBy(() -> apiController.checkName(1L, "taken"))
                .isInstanceOf(DuplicateNameException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.");
    }

    @Test
    @DisplayName("유효하지 않은 닉네임이면 bad request 예외를 전파한다")
    void checkName_propagatesBadRequestException() {
        when(userService.ensureNameAvailable(1L, "a"))
                .thenThrow(new BadRequestBusinessException("NAME_TOO_SHORT", "닉네임은 최소 2자 이상이어야 합니다."));

        assertThatThrownBy(() -> apiController.checkName(1L, "a"))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessage("닉네임은 최소 2자 이상이어야 합니다.");
    }

    @Test
    @DisplayName("사용 가능한 handle이면 success 응답을 반환한다")
    void checkHandle_returnsSuccessWhenAvailable() {
        when(userService.checkHandleAvailability(" @Slugger "))
                .thenReturn(new AvailabilityCheckResponseDto(true, "@slugger"));

        ResponseEntity<ApiResponse> response = apiController.checkHandle(" @Slugger ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(new AvailabilityCheckResponseDto(true, "@slugger"));
    }

    @Test
    @DisplayName("중복 handle이면 conflict 응답을 반환한다")
    void checkHandle_returnsConflictWhenTaken() {
        when(userService.checkHandleAvailability("@slugger"))
                .thenReturn(new AvailabilityCheckResponseDto(false, "@slugger"));

        ResponseEntity<ApiResponse> response = apiController.checkHandle("@slugger");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo("HANDLE_UNAVAILABLE");
        assertThat(response.getBody().getData()).isEqualTo(new AvailabilityCheckResponseDto(false, "@slugger"));
    }

    @Test
    @DisplayName("handle availability DB timeout returns temporary database error")
    void checkHandle_transientDataAccessException_returns503() throws Exception {
        when(userService.checkHandleAvailability("@slugger"))
                .thenThrow(new TransientDataAccessResourceException("connection pool exhausted"));

        MockMvcBuilders.standaloneSetup(apiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/auth/check-handle").param("handle", "@slugger"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TEMPORARY_DATABASE_ERROR"))
                .andExpect(jsonPath("$.message").value("서버가 현재 혼잡합니다. 잠시 후 다시 시도해주세요."));
    }

    // [Security Fix - Critical #3] /check-email 엔드포인트 제거에 따라 관련 테스트 제거.
    // 이메일 존재 여부를 노출하지 않는 것이 User Enumeration 방어 원칙.
}
