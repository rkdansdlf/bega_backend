package com.example.mypage.dto;

import com.example.auth.entity.TrustedDevice;
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
public class TrustedDeviceDto {
    private Long id;
    private String deviceLabel;
    private String deviceType;
    private String browser;
    private String os;
    private String firstSeenAt;
    private String lastSeenAt;
    private String lastLoginAt;
    private String lastIp;

    public static TrustedDeviceDto from(TrustedDevice device) {
        return TrustedDeviceDto.builder()
                .id(device.getId())
                .deviceLabel(device.getDeviceLabel())
                .deviceType(device.getDeviceType())
                .browser(device.getBrowser())
                .os(device.getOs())
                .firstSeenAt(formatDateTime(device.getFirstSeenAt()))
                .lastSeenAt(formatDateTime(device.getLastSeenAt()))
                .lastLoginAt(formatDateTime(device.getLastLoginAt()))
                .lastIp(device.getLastIp())
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
