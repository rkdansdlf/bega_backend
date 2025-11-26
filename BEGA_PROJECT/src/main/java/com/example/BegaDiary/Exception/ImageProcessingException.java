package com.example.BegaDiary.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 500 Internal Server Error
@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
public class ImageProcessingException extends RuntimeException {
    public ImageProcessingException(String message) {
        super("이미지 처리 중 오류가 발생했습니다: " + message);
    }
}