package com.example.auth.oauth2;

import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.bega.auth.service.OAuth2LinkStateService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookieAuthorizationRequestRepositoryTest {

    @Mock
    private OAuth2LinkStateService oAuth2LinkStateService;

    @Mock
    private AuthSecurityMonitoringService securityMonitoringService;

    private CookieAuthorizationRequestRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CookieAuthorizationRequestRepository(
                oAuth2LinkStateService,
                "test-cookie-secret",
                false,
                securityMonitoringService);
    }

    @Test
    @DisplayName("link 모드 + 유효한 one-time ticket이면 원본 state 키로 연동 데이터 저장")
    void saveAuthorizationRequest_savesLinkStateForValidLinkTicket() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("mode", "link");
        request.setParameter("linkToken", "valid-link-ticket");

        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = createAuthorizationRequest("raw-state-1");

        when(oAuth2LinkStateService.consumeLinkToken("valid-link-ticket"))
                .thenReturn(OAuth2LinkStateService.LinkTicketConsumeResult.success(42L));

        repository.saveAuthorizationRequest(authRequest, request, response);

        ArgumentCaptor<OAuth2LinkStateData> dataCaptor = ArgumentCaptor.forClass(OAuth2LinkStateData.class);
        verify(oAuth2LinkStateService).saveLinkByState(eq("raw-state-1"), dataCaptor.capture());

        OAuth2LinkStateData saved = dataCaptor.getValue();
        assertThat(saved.userId()).isEqualTo(42L);
        assertThat(saved.failureReason()).isNull();

        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(header -> header.startsWith("oauth2_auth_request="));
        assertThat(setCookies).noneMatch(header -> header.startsWith("redirect_uri="));
    }

    @Test
    @DisplayName("link 모드 + 재사용 ticket이면 replay 실패 사유를 저장")
    void saveAuthorizationRequest_recordsReplayFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("mode", "link");
        request.setParameter("linkToken", "replayed-ticket");

        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = createAuthorizationRequest("raw-state-2");

        when(oAuth2LinkStateService.consumeLinkToken("replayed-ticket"))
                .thenReturn(OAuth2LinkStateService.LinkTicketConsumeResult.failure("oauth2_link_replayed"));

        repository.saveAuthorizationRequest(authRequest, request, response);

        ArgumentCaptor<OAuth2LinkStateData> dataCaptor = ArgumentCaptor.forClass(OAuth2LinkStateData.class);
        verify(oAuth2LinkStateService).saveLinkByState(eq("raw-state-2"), dataCaptor.capture());

        OAuth2LinkStateData saved = dataCaptor.getValue();
        assertThat(saved.userId()).isNull();
        assertThat(saved.failureReason()).isEqualTo("oauth2_link_replayed");
    }

    @Test
    @DisplayName("일반 로그인 모드면 연동 state 저장하지 않음")
    void saveAuthorizationRequest_doesNotSaveLinkStateForNormalMode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("mode", "login");

        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = createAuthorizationRequest("raw-state-normal");

        repository.saveAuthorizationRequest(authRequest, request, response);

        verifyNoInteractions(oAuth2LinkStateService);
    }

    @Test
    @DisplayName("link 모드 저장 시 쿠키에 mode=link 속성이 보존됨")
    void saveAuthorizationRequest_preservesLinkModeInCookieAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("mode", "link");
        request.setParameter("linkToken", "valid-ticket");

        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = createAuthorizationRequest("raw-state-4");

        when(oAuth2LinkStateService.consumeLinkToken("valid-ticket"))
                .thenReturn(OAuth2LinkStateService.LinkTicketConsumeResult.success(7L));

        repository.saveAuthorizationRequest(authRequest, request, response);

        String authCookieValue = extractCookieValue(response, CookieAuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setCookies(new Cookie(CookieAuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, authCookieValue));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(callbackRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("raw-state-4");
        assertThat((String) loaded.getAttribute("mode")).isEqualTo("link");
    }

    @Test
    @DisplayName("state 쿠키가 없으면 loadAuthorizationRequest는 null")
    void loadAuthorizationRequest_returnsNullWhenCookieMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    @DisplayName("null authorizationRequest 저장 시 쿠키 제거 요청")
    void saveAuthorizationRequest_nullRequestRemovesCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(CookieAuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "value"),
                new Cookie(CookieAuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME, "http://localhost")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(null, request, response);

        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(header -> header.startsWith("oauth2_auth_request=;"));
        assertThat(setCookies).anyMatch(header -> header.startsWith("redirect_uri=;"));
    }

    @Test
    @DisplayName("잘못된 oauth2_auth_request 쿠키는 null 처리")
    void loadAuthorizationRequest_returnsNullForBrokenCookiePayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(
                CookieAuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                "not-a-valid-cookie"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
        verify(securityMonitoringService).recordUnsignedOauth2Cookie();
    }

    @Test
    @DisplayName("link ticket 없이 link 모드면 저장 실패 사유를 남긴다")
    void saveAuthorizationRequest_recordsFailureWhenLinkTicketMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("mode", "link");

        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = createAuthorizationRequest("raw-state-5");

        when(oAuth2LinkStateService.consumeLinkToken(null))
                .thenReturn(OAuth2LinkStateService.LinkTicketConsumeResult.failure("oauth2_link_failed"));

        repository.saveAuthorizationRequest(authRequest, request, response);

        ArgumentCaptor<OAuth2LinkStateData> dataCaptor = ArgumentCaptor.forClass(OAuth2LinkStateData.class);
        verify(oAuth2LinkStateService).saveLinkByState(eq("raw-state-5"), dataCaptor.capture());
        assertThat(dataCaptor.getValue().failureReason()).isEqualTo("oauth2_link_failed");
        verify(oAuth2LinkStateService, never()).consumeLinkByState("raw-state-5");
    }

    private OAuth2AuthorizationRequest createAuthorizationRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(Set.of("email", "profile"))
                .state(state)
                .authorizationRequestUri("https://accounts.google.com/o/oauth2/v2/auth?client_id=client-id&state=" + state)
                .build();
    }

    private String extractCookieValue(MockHttpServletResponse response, String cookieName) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .findFirst()
                .map(header -> {
                    int start = cookieName.length() + 1;
                    int end = header.indexOf(';');
                    return end > start ? header.substring(start, end) : header.substring(start);
                })
                .orElseThrow(() -> new IllegalStateException("쿠키가 존재하지 않습니다: " + cookieName));
    }
}
