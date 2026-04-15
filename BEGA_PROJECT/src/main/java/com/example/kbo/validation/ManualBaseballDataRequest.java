package com.example.kbo.validation;

import java.util.List;

public record ManualBaseballDataRequest(
        String scope,
        List<ManualBaseballDataMissingItem> missingItems,
        String operatorMessage,
        boolean blocking) {}
