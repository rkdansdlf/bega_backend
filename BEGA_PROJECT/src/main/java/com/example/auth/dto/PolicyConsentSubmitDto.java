package com.example.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
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
public class PolicyConsentSubmitDto {

    @Valid
    @NotEmpty(message = "필수 정책 동의 목록이 필요합니다.")
    private List<PolicyConsentItemDto> policyConsents;
}

