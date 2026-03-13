package com.example.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;
    private String code;
    private Map<String, String> errors;

    public static ApiResponse success(String message) {
        return new ApiResponse(true, message, null, null, null);
    }

    public static ApiResponse success(String message, Object data) {
        return new ApiResponse(true, message, data, null, null);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null, "ERROR", null);
    }

    public static ApiResponse error(String message, Object data) {
        return new ApiResponse(false, message, data, "ERROR", null);
    }

    public static ApiResponse error(String code, String message) {
        return new ApiResponse(false, message, null, code, null);
    }

    public static ApiResponse error(String code, String message, Object data) {
        return new ApiResponse(false, message, data, code, null);
    }

    public static ApiResponse error(String code, String message, Map<String, String> errors) {
        return new ApiResponse(false, message, null, code, errors);
    }

    public static ApiResponse error(String code, String message, Object data, Map<String, String> errors) {
        return new ApiResponse(false, message, data, code, errors);
    }
}
