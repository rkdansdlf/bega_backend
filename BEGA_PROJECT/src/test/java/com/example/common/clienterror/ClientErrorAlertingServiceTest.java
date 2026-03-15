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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ClientErrorMonitoringProperties properties;
    private ClientErrorAlertingService clientErrorAlertingService;

    @BeforeEach
    void setUp() {
        properties = new ClientErrorMonitoringProperties();
        properties.getAlerts().setEnabled(true);
        properties.getAlerts().setSlackWebhookUrl("https://hooks.slack.test/services/T000/B000/XXX");
        properties.getAlerts().setRuntimeThreshold(3);
        properties.getAlerts().setApi5xxThreshold(5);
        properties.getAlerts().setWindowMinutes(5);
        properties.getAlerts().setCooldownMinutes(30);

        clientErrorAlertingService = new ClientErrorAlertingService(
                eventRepository,
                feedbackRepository,
                alertNotificationRepository,
                properties,
                restClientBuilder);
        ReflectionTestUtils.setField(clientErrorAlertingService, "frontendUrl", "https://admin.example.com");
    }

    @Test
    @DisplayName("runtime threshold 충족 시 Slack 알림 기록을 저장한다")
    void evaluateAlertsSendsRuntimeAlert() {
        LocalDateTime now = LocalDateTime.now(ClientErrorSupport.UTC);
        List<ClientErrorEventEntity> events = List.of(
                runtimeEvent("evt-1", now.minusMinutes(4)),
                runtimeEvent("evt-2", now.minusMinutes(3)),
                runtimeEvent("evt-3", now.minusMinutes(1)));

        when(eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(any()))
                .thenReturn(events);
        when(alertNotificationRepository.existsByFingerprintAndNotifiedAtAfter(anyString(), any()))
                .thenReturn(false);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        clientErrorAlertingService.evaluateAlerts();

        ArgumentCaptor<ClientErrorAlertNotificationEntity> captor =
                ArgumentCaptor.forClass(ClientErrorAlertNotificationEntity.class);
        verify(alertNotificationRepository).save(captor.capture());
        assertThat(captor.getValue().getObservedCount()).isEqualTo(3L);
        assertThat(captor.getValue().getDeliveryStatus()).isNotNull();
        assertThat(captor.getValue().getLatestEventId()).isEqualTo("evt-3");
    }

    @Test
    @DisplayName("cooldown 안의 fingerprint는 재알림하지 않는다")
    void evaluateAlertsSkipsCooldownFingerprint() {
        LocalDateTime now = LocalDateTime.now(ClientErrorSupport.UTC);
        when(eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(any()))
                .thenReturn(List.of(
                        runtimeEvent("evt-1", now.minusMinutes(4)),
                        runtimeEvent("evt-2", now.minusMinutes(3)),
                        runtimeEvent("evt-3", now.minusMinutes(2))));
        when(alertNotificationRepository.existsByFingerprintAndNotifiedAtAfter(anyString(), any()))
                .thenReturn(true);

        clientErrorAlertingService.evaluateAlerts();

        verify(alertNotificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("webhook 설정이 없으면 알림 평가를 건너뛴다")
    void evaluateAlertsNoOpsWithoutWebhook() {
        properties.getAlerts().setSlackWebhookUrl("");

        clientErrorAlertingService.evaluateAlerts();

        verify(eventRepository, never()).findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(any());
        verify(alertNotificationRepository, never()).save(any());
    }

    private ClientErrorEventEntity runtimeEvent(String eventId, LocalDateTime occurredAt) {
        return ClientErrorEventEntity.builder()
                .eventId(eventId)
                .bucket(ClientErrorBucket.RUNTIME)
                .source(ClientErrorSource.RUNTIME)
                .message("render failed")
                .route("/mypage")
                .normalizedRoute("/mypage")
                .statusGroup("none")
                .occurredAt(occurredAt)
                .fingerprint("fp-runtime")
                .feedbackCount(0)
                .build();
    }
}
