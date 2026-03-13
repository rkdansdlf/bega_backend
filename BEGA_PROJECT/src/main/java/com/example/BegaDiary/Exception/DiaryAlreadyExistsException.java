package com.example.BegaDiary.Exception;

import com.example.common.exception.ConflictBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class DiaryAlreadyExistsException extends ConflictBusinessException {
    public DiaryAlreadyExistsException(String message) {
        super("DIARY_ALREADY_EXISTS", message);
    }
}
