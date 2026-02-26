package com.example.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PolicyRequirementItemDto {

    private String policyType;
    private String version;
    private String path;
    private boolean required;
    private String effectiveDate;
}

