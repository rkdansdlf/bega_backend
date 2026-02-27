package com.example.common.exception;

import java.util.List;

public class PolicyConsentException extends RuntimeException {

    private final String code;
    private final List<String> policyTypes;

    public PolicyConsentException(String code, String message, List<String> policyTypes) {
        super(message);
        this.code = code;
        this.policyTypes = policyTypes;
    }

    public String getCode() {
        return code;
    }

    public List<String> getPolicyTypes() {
        return policyTypes;
    }
}

