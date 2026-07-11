package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientErrorAlertingServiceTest {

    @Mock
    private ClientErrorEventRepository eventRepository;

    @Mock
    private ClientErrorFeedbackRepository feedbackRepository;

    @Mock
    private ClientErrorAlertNotificationRepository alertNotificationRepository;

    @Mock
    private ClientErrorAlertSender alertSender;

    private ClientErrorMonitoringProperties properties;
    private ClientErrorAlertingService clientErrorAlertingService;

    @BeforeEach
    void setUp() {
        properties = new ClientErrorMonitoringProperties();
        properties.getAlerts().setEnabled(true);
        properties.getAlerts().setChannel(ClientErrorAlertChannel.TELEGRAM);
        properties.getAlerts().getTelegram().setBotToken("telegram-bot-token");
        properties.getAlerts().getTelegram().setChatId("-1001234567890");
        properties.getAlerts().setRuntimeThreshold(3);
        properties.getAlerts().setApi5xxThreshold(5);
        properties.getAlerts().setWindowMinutes(5);
        properties.getAlerts().setCooldownMinutes(30);
        when(alertSender.channel()).thenReturn(ClientErrorAlertChannel.TELEGRAM);
        when(alertSender.isConfigured(properties.getAlerts())).thenReturn(true);

        clientErrorAlertingService = new ClientErrorAlertingService(
                eventRepository,
                feedbackRepository,
                alertNotificationRepository,
                properties,
                List.of(alertSender));
        ReflectionTestUtils.setField(clientErrorAlertingService, "frontendUrl", "https://admin.example.com");
    }

    @Test
    @DisplayName("runtime threshold 충족 시 Telegram 알림 기록을 저장한다")
    void evaluateAlertsSendsRuntimeAlert() {
        LocalDateTime now = LocalDateTime.now(ClientErrorSupport.UTC);
        when(eventRepository.findAlertCandidateSummaries(any(), any(), any(), any(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(summary("fp-runtime", 3, now.minusMinutes(1))));
        when(eventRepository.findLatestAlertEventsByFingerprint(any(), any(), any(), any(), any()))
                .thenReturn(List.of(runtimeEvent("evt-3", now.minusMinutes(1))));
        when(alertNotificationRepository.findFingerprintsNotifiedAfter(any(), any()))
                .thenReturn(List.of());
        when(alertSender.send(any(), any()))
                .thenReturn(new ClientErrorAlertDeliveryResult(ClientErrorAlertDeliveryStatus.SENT, null));

        clientErrorAlertingService.evaluateAlerts();

        ArgumentCaptor<ClientErrorAlertNotificationEntity> captor =
                ArgumentCaptor.forClass(ClientErrorAlertNotificationEntity.class);
        verify(alertNotificationRepository).save(captor.capture());
        assertThat(captor.getValue().getObservedCount()).isEqualTo(3L);
        assertThat(captor.getValue().getDeliveryStatus()).isNotNull();
        assertThat(captor.getValue().getLatestEventId()).isEqualTo("evt-3");
        assertThat(captor.getValue().getChannel()).isEqualTo(ClientErrorAlertChannel.TELEGRAM);
        verify(alertSender).send(any(), any());
        verify(eventRepository, never()).findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(any());
        verify(alertNotificationRepository, never()).existsByFingerprintAndNotifiedAtAfter(any(), any());
        verify(eventRepository).findAlertCandidateSummaries(
                any(),
                eq(ClientErrorBucket.RUNTIME),
                eq(ClientErrorBucket.API),
                eq("5xx"),
                anyLong(),
                argThat(pageable -> pageable.getPageSize() == 1000));
    }

    @Test
    @DisplayName("cooldown 안의 fingerprint는 재알림하지 않는다")
    void evaluateAlertsSkipsCooldownFingerprint() {
        LocalDateTime now = LocalDateTime.now(ClientErrorSupport.UTC);
        when(eventRepository.findAlertCandidateSummaries(any(), any(), any(), any(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(summary("fp-runtime", 3, now.minusMinutes(2))));
        when(eventRepository.findLatestAlertEventsByFingerprint(any(), any(), any(), any(), any()))
                .thenReturn(List.of(runtimeEvent("evt-3", now.minusMinutes(2))));
        when(alertNotificationRepository.findFingerprintsNotifiedAfter(any(), any()))
                .thenReturn(List.of("fp-runtime"));

        clientErrorAlertingService.evaluateAlerts();

        verify(alertNotificationRepository, never()).save(any());
        verify(alertSender, never()).send(any(), any());
        verify(alertNotificationRepository, never()).existsByFingerprintAndNotifiedAtAfter(any(), any());
    }

    @Test
    @DisplayName("API 5xx threshold 미만 후보는 쿨다운 조회 전에 건너뛴다")
    void evaluateAlertsSkipsApiCandidateBelowThreshold() {
        LocalDateTime now = LocalDateTime.now(ClientErrorSupport.UTC);
        when(eventRepository.findAlertCandidateSummaries(any(), any(), any(), any(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(summary("fp-api", 4, now.minusMinutes(1))));
        when(eventRepository.findLatestAlertEventsByFingerprint(any(), any(), any(), any(), any()))
                .thenReturn(List.of(apiEvent("evt-api", now.minusMinutes(1), "5xx")));

        clientErrorAlertingService.evaluateAlerts();

        verify(alertNotificationRepository, never()).findFingerprintsNotifiedAfter(any(), any());
        verify(alertNotificationRepository, never()).save(any());
        verify(alertSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("maxCandidates는 최소 1개로 보정해 후보 조회를 제한한다")
    void evaluateAlertsNormalizesMaxCandidatesLowerBound() {
        properties.getAlerts().setMaxCandidates(0);
        when(eventRepository.findAlertCandidateSummaries(any(), any(), any(), any(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        clientErrorAlertingService.evaluateAlerts();

        verify(eventRepository).findAlertCandidateSummaries(
                any(),
                eq(ClientErrorBucket.RUNTIME),
                eq(ClientErrorBucket.API),
                eq("5xx"),
                anyLong(),
                argThat(pageable -> pageable.getPageSize() == 1));
    }

    @Test
    @DisplayName("Telegram 설정이 없으면 알림 평가를 건너뛴다")
    void evaluateAlertsNoOpsWithoutTelegramConfiguration() {
        when(alertSender.isConfigured(properties.getAlerts())).thenReturn(false);

        clientErrorAlertingService.evaluateAlerts();

        verify(eventRepository, never()).findAlertCandidateSummaries(any(), any(), any(), any(), anyLong(), any(Pageable.class));
        verify(alertNotificationRepository, never()).save(any());
    }

    private ClientErrorAlertCandidateProjection summary(String fingerprint, long observedCount, LocalDateTime latestOccurredAt) {
        return new ClientErrorAlertCandidateProjection() {
            @Override
            public String getFingerprint() {
                return fingerprint;
            }

            @Override
            public long getObservedCount() {
                return observedCount;
            }

            @Override
            public LocalDateTime getLatestOccurredAt() {
                return latestOccurredAt;
            }
        };
    }

    private ClientErrorEventSummaryProjection runtimeEvent(String eventId, LocalDateTime occurredAt) {
        return event(
                eventId,
                ClientErrorBucket.RUNTIME,
                ClientErrorSource.RUNTIME,
                "render failed",
                "/mypage",
                "/mypage",
                null,
                "none",
                null,
                null,
                null,
                "fp-runtime",
                occurredAt,
                0);
    }

    private ClientErrorEventSummaryProjection apiEvent(String eventId, LocalDateTime occurredAt, String statusGroup) {
        return event(
                eventId,
                ClientErrorBucket.API,
                ClientErrorSource.API,
                "api failed",
                "/api/mate",
                "/api/mate",
                500,
                statusGroup,
                "GET",
                "/api/mate",
                "/api/mate",
                "fp-api",
                occurredAt,
                0);
    }

    private ClientErrorEventSummaryProjection event(
            String eventId,
            ClientErrorBucket bucket,
            ClientErrorSource source,
            String message,
            String route,
            String normalizedRoute,
            Integer statusCode,
            String statusGroup,
            String method,
            String endpoint,
            String normalizedEndpoint,
            String fingerprint,
            LocalDateTime occurredAt,
            Integer feedbackCount) {
        return new ClientErrorEventSummaryProjection() {
            @Override
            public String getEventId() {
                return eventId;
            }

            @Override
            public ClientErrorBucket getBucket() {
                return bucket;
            }

            @Override
            public ClientErrorSource getSource() {
                return source;
            }

            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public Integer getStatusCode() {
                return statusCode;
            }

            @Override
            public String getStatusGroup() {
                return statusGroup;
            }

            @Override
            public String getResponseCode() {
                return null;
            }

            @Override
            public String getRoute() {
                return route;
            }

            @Override
            public String getNormalizedRoute() {
                return normalizedRoute;
            }

            @Override
            public String getMethod() {
                return method;
            }

            @Override
            public String getEndpoint() {
                return endpoint;
            }

            @Override
            public String getNormalizedEndpoint() {
                return normalizedEndpoint;
            }

            @Override
            public String getFingerprint() {
                return fingerprint;
            }

            @Override
            public LocalDateTime getOccurredAt() {
                return occurredAt;
            }

            @Override
            public String getSessionId() {
                return null;
            }

            @Override
            public Long getUserId() {
                return null;
            }

            @Override
            public Integer getFeedbackCount() {
                return feedbackCount;
            }
        };
    }
}
