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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        List<ClientErrorEventEntity> events = List.of(
                runtimeEvent("evt-1", now.minusMinutes(4)),
                runtimeEvent("evt-2", now.minusMinutes(3)),
                runtimeEvent("evt-3", now.minusMinutes(1)));

        when(eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(any()))
                .thenReturn(events);
        when(alertNotificationRepository.existsByFingerprintAndNotifiedAtAfter(any(), any()))
                .thenReturn(false);
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
        when(alertNotificationRepository.existsByFingerprintAndNotifiedAtAfter(any(), any()))
                .thenReturn(true);

        clientErrorAlertingService.evaluateAlerts();

        verify(alertNotificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Telegram 설정이 없으면 알림 평가를 건너뛴다")
    void evaluateAlertsNoOpsWithoutTelegramConfiguration() {
        when(alertSender.isConfigured(properties.getAlerts())).thenReturn(false);

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
