package com.example.mypage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProviderDto {
    private String provider;
    private String providerId;
    private String email;
    private String connectedAt;
}
