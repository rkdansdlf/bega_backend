package com.example.common.exception;

import java.util.Map;

public class DuplicateHandleException extends ConflictBusinessException {

    public DuplicateHandleException(String handle) {
        super("HANDLE_UNAVAILABLE", "이미 사용 중인 아이디(@handle)입니다.", Map.of("handle", handle));
    }
}
