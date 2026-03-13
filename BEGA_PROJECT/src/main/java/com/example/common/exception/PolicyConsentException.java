package com.example.common.exception;

import java.util.List;
import java.util.Map;

public class PolicyConsentException extends BadRequestBusinessException {

    public PolicyConsentException(String code, String message, List<String> policyTypes) {
        super(code, message, Map.of("policyTypes", policyTypes));
    }
}
