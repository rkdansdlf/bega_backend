package com.example.mypage.dto;

import com.example.auth.entity.TrustedDevice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
                .firstSeenAt(device.getFirstSeenAt() == null ? null : device.getFirstSeenAt().toString())
                .lastSeenAt(device.getLastSeenAt() == null ? null : device.getLastSeenAt().toString())
                .lastLoginAt(device.getLastLoginAt() == null ? null : device.getLastLoginAt().toString())
                .lastIp(device.getLastIp())
                .build();
    }
}
