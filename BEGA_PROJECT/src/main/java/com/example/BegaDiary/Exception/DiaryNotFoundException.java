package com.example.BegaDiary.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 404 Not Found 상태를 반환하도록 설정
@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class DiaryNotFoundException extends RuntimeException {
    public DiaryNotFoundException(Long id) {
        super("해당 다이어리를 찾을 수 없습니다. ID: " + id);
    }
}