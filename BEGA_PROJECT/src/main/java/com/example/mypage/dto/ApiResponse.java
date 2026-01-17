package com.example.mypage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse {
    
    // 응답 상태 (true: 성공, false: 오류)
    private final boolean success;
    
    // 응답 메시지
    private final String message;
    
    // 실제 데이터 페이로드
    private final Object data;

    // 성공적인 응답을 생성하는 팩토리 
    public static ApiResponse success(String message) {
        return new ApiResponse(true, message, null);
    }

    // 성공적인 응답을 생성하는 팩토리 메서드 (데이터 포함)
    public static ApiResponse success(String message, Object data) {
        return new ApiResponse(true, message, data);
    }
    
    // 오류 응답을 생성하는 팩토리 메서드 (데이터 없이 메시지만 전달)
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null);
    }
}

