package com.example.auth.dto;

import com.example.auth.entity.PolicyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyConsentItemDto {

    @NotNull(message = "정책 유형은 필수입니다.")
    private PolicyType policyType;

    @NotBlank(message = "정책 버전은 필수입니다.")
    private String version;

    @NotNull(message = "정책 동의 여부는 필수입니다.")
    private Boolean agreed;
}

