package com.example.BegaDiary.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class DiaryAlreadyExistsException extends RuntimeException {
    public DiaryAlreadyExistsException(String message) {
        super(message);
    }
}
