package com.example.common.clienterror;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.BDDMockito.given;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.CustomUserDetails;
import com.example.common.clienterror.dto.ClientErrorEventRequest;
import com.example.common.clienterror.dto.ClientErrorFeedbackRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ClientErrorLoggingServiceTest {

    @Mock
    private ClientErrorEventRepository eventRepository;

    @Mock
    private ClientErrorFeedbackRepository feedbackRepository;

    @Mock
    private Authentication authentication;

    private ClientErrorLoggingService service;

    @BeforeEach
    void setUp() {
        service = new ClientErrorLoggingService(
                new SimpleMeterRegistry(),
                eventRepository,
                feedbackRepository);
        lenient().when(eventRepository.existsByEventId(anyString())).thenReturn(false);
    }

    @Test
    void unauthenticatedClientErrorIgnoresRequestUserId() {
        service.logClientError(requestWithUserId("evt-unauth", 999L), null);

        ArgumentCaptor<ClientErrorEventEntity> entityCaptor = ArgumentCaptor.forClass(ClientErrorEventEntity.class);
        verify(eventRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUserId()).isNull();
    }

    @Test
    void authenticatedClientErrorStoresPrincipalUserId() {
        CustomUserDetails principal = new CustomUserDetails(UserEntity.builder()
                .id(42L)
                .email("user@example.com")
                .password("encoded")
                .role("ROLE_USER")
                .enabled(true)
                .build());
        given(authentication.isAuthenticated()).willReturn(true);
        given(authentication.getPrincipal()).willReturn(principal);

        service.logClientError(requestWithUserId("evt-auth", 999L), authentication);

        ArgumentCaptor<ClientErrorEventEntity> entityCaptor = ArgumentCaptor.forClass(ClientErrorEventEntity.class);
        verify(eventRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUserId()).isEqualTo(42L);
    }

    @Test
    void clientErrorRedactsTokenizedUrlsAndHashesSessionIdBeforeStorage() {
        ClientErrorEventRequest request = new ClientErrorEventRequest(
                "evt-redact",
                "api",
                "Request failed Authorization: Bearer raw-access-token",
                500,
                "UPSTREAM_ERROR",
                "Error: token=raw-stack-token",
                "state=raw-component-state",
                "/password/reset/confirm?token=raw-reset-token&redirect=/mypage#frag",
                "POST",
                "/api/auth/password/reset/confirm?token=raw-endpoint-token",
                "2026-06-03T00:00:00Z",
                "raw-session-id",
                999L);

        service.logClientError(request, null);

        ArgumentCaptor<ClientErrorEventEntity> entityCaptor = ArgumentCaptor.forClass(ClientErrorEventEntity.class);
        verify(eventRepository).save(entityCaptor.capture());
        ClientErrorEventEntity saved = entityCaptor.getValue();
        assertThat(saved.getRoute()).isEqualTo("/password/reset/confirm");
        assertThat(saved.getEndpoint()).isEqualTo("/api/auth/password/reset/confirm");
        assertThat(saved.getMessage()).doesNotContain("raw-access-token");
        assertThat(saved.getStack()).doesNotContain("raw-stack-token");
        assertThat(saved.getComponentStack()).doesNotContain("raw-component-state");
        assertThat(saved.getSessionId()).startsWith("sha256:");
        assertThat(saved.getSessionId()).doesNotContain("raw-session-id");
    }

    @Test
    void feedbackRedactsSensitiveTextAndStripsRouteQueryBeforeStorage() {
        ClientErrorFeedbackRequest request = new ClientErrorFeedbackRequest(
                "evt-feedback",
                "Please help token=raw-feedback-token Authorization: Bearer raw-bearer",
                "reported",
                "/account/deletion/recovery?token=raw-recovery-token",
                "2026-06-03T00:00:00Z");

        service.logClientFeedback(request, null);

        ArgumentCaptor<ClientErrorFeedbackEntity> entityCaptor = ArgumentCaptor.forClass(ClientErrorFeedbackEntity.class);
        verify(feedbackRepository).save(entityCaptor.capture());
        ClientErrorFeedbackEntity saved = entityCaptor.getValue();
        assertThat(saved.getRoute()).isEqualTo("/account/deletion/recovery");
        assertThat(saved.getComment()).doesNotContain("raw-feedback-token");
        assertThat(saved.getComment()).doesNotContain("raw-bearer");
    }

    private ClientErrorEventRequest requestWithUserId(String eventId, Long userId) {
        return new ClientErrorEventRequest(
                eventId,
                "api",
                "Request failed",
                500,
                "UPSTREAM_ERROR",
                null,
                null,
                "/mypage",
                "GET",
                "/api/mypage",
                "2026-06-03T00:00:00Z",
                "session-1",
                userId);
    }
}
