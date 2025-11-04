package com.example.BegaDiary.Exception;

public class DiaryAlreadyExistsException extends RuntimeException {
    public DiaryAlreadyExistsException(String message) {
        super(message);
    }
}
