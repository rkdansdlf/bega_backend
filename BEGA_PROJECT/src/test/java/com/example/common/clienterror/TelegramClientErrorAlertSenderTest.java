package com.example.common.clienterror;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramClientErrorAlertSenderTest {

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

    private TelegramClientErrorAlertSender sender;
    private ClientErrorMonitoringProperties.Alerts alerts;
    private ClientErrorAlertPayload payload;

    @BeforeEach
    void setUp() {
        sender = new TelegramClientErrorAlertSender(restClientBuilder);
        alerts = new ClientErrorMonitoringProperties().getAlerts();
        alerts.getTelegram().setBotToken("telegram-token");
        alerts.getTelegram().setChatId("-100987654321");
        payload = new ClientErrorAlertPayload(
                ClientErrorBucket.RUNTIME,
                ClientErrorSource.RUNTIME,
                3,
                5,
                "/mypage",
                "none",
                "evt-3",
                "fp-runtime",
                "render failed",
                "https://admin.example.com/admin");
    }

    @Test
    @DisplayName("Telegram sendMessage 호출 성공 시 SENT를 반환한다")
    void sendReturnsSentOnSuccess() {
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(anyMap());
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        ClientErrorAlertDeliveryResult result = sender.send(payload, alerts);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).body(bodyCaptor.capture());
        assertThat(result.status()).isEqualTo(ClientErrorAlertDeliveryStatus.SENT);
        assertThat(bodyCaptor.getValue()).containsEntry("chat_id", "-100987654321");
        assertThat(bodyCaptor.getValue()).containsEntry("disable_web_page_preview", true);
        assertThat(bodyCaptor.getValue().get("text")).asString().contains("adminUrl=https://admin.example.com/admin");
    }

    @Test
    @DisplayName("Telegram API 실패 시 FAILED와 실패 사유를 반환한다")
    void sendReturnsFailedOnTelegramError() {
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(anyMap());
        when(requestBodySpec.retrieve()).thenThrow(new RuntimeException("telegram rejected request"));

        ClientErrorAlertDeliveryResult result = sender.send(payload, alerts);

        assertThat(result.status()).isEqualTo(ClientErrorAlertDeliveryStatus.FAILED);
        assertThat(result.failureReason()).contains("telegram rejected request");
    }
}
