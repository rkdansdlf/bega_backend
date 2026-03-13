package com.example.mypage.dto;

import com.example.auth.entity.AccountSecurityEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
                .occurredAt(event.getOccurredAt() == null ? null : event.getOccurredAt().toString())
                .deviceLabel(event.getDeviceLabel())
                .deviceType(event.getDeviceType())
                .browser(event.getBrowser())
                .os(event.getOs())
                .ip(event.getIp())
                .message(event.getMessage())
                .build();
    }
}
