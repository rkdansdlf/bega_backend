package com.example.kbo.validation;

import com.example.common.exception.ConflictBusinessException;

public class ManualBaseballDataRequiredException extends ConflictBusinessException {

    public static final String CODE = "MANUAL_BASEBALL_DATA_REQUIRED";
    public static final String PUBLIC_MESSAGE = "야구 데이터 준비가 필요합니다. 운영자가 데이터를 제공하면 다시 확인할 수 있습니다.";

    public ManualBaseballDataRequiredException(ManualBaseballDataRequest request) {
        super(CODE, PUBLIC_MESSAGE, request);
    }
}
