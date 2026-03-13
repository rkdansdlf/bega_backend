package com.example.admin.exception;

import com.example.common.exception.NotFoundBusinessException;

public class OffseasonMovementNotFoundException extends NotFoundBusinessException {

    public OffseasonMovementNotFoundException(Long id) {
        super("OFFSEASON_MOVEMENT_NOT_FOUND", "이동 정보를 찾을 수 없습니다. id=" + id);
    }
}
