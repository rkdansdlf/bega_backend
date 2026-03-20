package com.example.mypage.dto;

import com.example.auth.entity.AccountSecurityEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSecurityEventDto {
    private Long id;
    private String eventType;
    private String occurredAt;
    private String deviceLabel;
    private String deviceType;
    private String browser;
    private String os;
    private String ip;
    private String message;

    public static AccountSecurityEventDto from(AccountSecurityEvent event) {
        return AccountSecurityEventDto.builder()
                .id(event.getId())
                .eventType(event.getEventType().name())
                .occurredAt(formatDateTime(event.getOccurredAt()))
                .deviceLabel(event.getDeviceLabel())
                .deviceType(event.getDeviceType())
                .browser(event.getBrowser())
                .os(event.getOs())
                .ip(event.getIp())
                .message(event.getMessage())
                .build();
    }

    private static String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
