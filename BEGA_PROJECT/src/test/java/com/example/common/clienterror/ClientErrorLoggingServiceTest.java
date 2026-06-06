package com.example.common.clienterror;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.CustomUserDetails;
import com.example.common.clienterror.dto.ClientErrorEventRequest;
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
        given(eventRepository.existsByEventId(anyString())).willReturn(false);
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
