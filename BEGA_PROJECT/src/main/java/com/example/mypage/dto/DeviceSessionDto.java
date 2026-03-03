package com.example.mypage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSessionDto {
    private String id;
    private String sessionName;
    private String deviceLabel;
    private String deviceType;
    private String browser;
    private String os;
    private String lastActiveAt;
    private String lastSeenAt;
    @JsonProperty("isCurrent")
    private boolean isCurrent;
    @JsonProperty("isRevoked")
    private boolean isRevoked;
    private String ip;
}
