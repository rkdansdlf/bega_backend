package com.example.common.clienterror;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramClientErrorAlertSender implements ClientErrorAlertSender {

    private final RestClient.Builder restClientBuilder;

    @Override
    public ClientErrorAlertChannel channel() {
        return ClientErrorAlertChannel.TELEGRAM;
    }

    @Override
    public boolean isConfigured(ClientErrorMonitoringProperties.Alerts alerts) {
        ClientErrorMonitoringProperties.Telegram telegram = alerts.getTelegram();
        return telegram != null
                && StringUtils.hasText(telegram.getBotToken())
                && StringUtils.hasText(telegram.getChatId());
    }

    @Override
    public ClientErrorAlertDeliveryResult send(
            ClientErrorAlertPayload payload,
            ClientErrorMonitoringProperties.Alerts alerts) {
        String botToken = alerts.getTelegram().getBotToken();
        String chatId = alerts.getTelegram().getChatId();
        String apiUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String message = buildTelegramText(payload);

        try {
            restClientBuilder.build()
                    .post()
                    .uri(apiUrl)
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", message,
                            "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
            return new ClientErrorAlertDeliveryResult(ClientErrorAlertDeliveryStatus.SENT, null);
        } catch (Exception e) {
            log.warn("Client error Telegram alert delivery failed fingerprint={}", payload.fingerprint(), e);
            return new ClientErrorAlertDeliveryResult(
                    ClientErrorAlertDeliveryStatus.FAILED,
                    ClientErrorSupport.sanitize(e.getMessage(), ClientErrorSupport.MESSAGE_LOG_LIMIT));
        }
    }

    private String buildTelegramText(ClientErrorAlertPayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("[BEGA Client Error Alert]\n");
        builder.append("bucket=").append(payload.bucket().getValue()).append('\n');
        builder.append("source=").append(payload.source().getValue()).append('\n');
        builder.append("count=").append(payload.count()).append(" in last ").append(payload.windowMinutes()).append("m\n");
        builder.append("route=").append(payload.route()).append('\n');
        builder.append("statusGroup=").append(payload.statusGroup()).append('\n');
        builder.append("eventId=").append(payload.latestEventId()).append('\n');
        builder.append("fingerprint=").append(payload.fingerprint()).append('\n');
        builder.append("message=").append(payload.latestMessage()).append('\n');
        builder.append("adminUrl=").append(payload.adminUrl());
        return builder.toString();
    }
}
