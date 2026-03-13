package com.example.common.exception;

import java.util.Map;

public class DuplicateNameException extends ConflictBusinessException {

    public DuplicateNameException(String name) {
        super("NAME_UNAVAILABLE", "이미 사용 중인 닉네임입니다.", Map.of("name", name));
    }
}
