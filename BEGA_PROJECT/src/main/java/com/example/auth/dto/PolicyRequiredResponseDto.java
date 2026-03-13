package com.example.auth.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PolicyRequiredResponseDto {

    private List<PolicyRequirementItemDto> policies;
    private int gracePeriodDays;
    private String effectiveDate;
    private String hardGateDate;
}

