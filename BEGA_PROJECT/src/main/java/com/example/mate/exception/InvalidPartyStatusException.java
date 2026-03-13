package com.example.mate.exception;

import com.example.common.exception.BadRequestBusinessException;

public class InvalidPartyStatusException extends BadRequestBusinessException {
    public InvalidPartyStatusException(String status) {
        super("INVALID_PARTY_STATUS", "잘못된 파티 상태입니다: " + status);
    }
}
