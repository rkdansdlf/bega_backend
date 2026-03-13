package com.example.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.auth.repository.RefreshRepository;
import com.example.auth.service.AuthRegistrationService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.service.UserService;
import com.example.auth.util.AuthCookieUtil;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.DuplicateNameException;
import com.example.common.web.ClientIpResolver;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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

    private final AuthCookieUtil authCookieUtil = new AuthCookieUtil(false);

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
}
