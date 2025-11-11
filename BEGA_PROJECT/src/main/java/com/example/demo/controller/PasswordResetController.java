package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.PasswordResetConfirmDto;
import com.example.demo.dto.PasswordResetRequestDto;
import com.example.demo.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {
    
    private final PasswordResetService passwordResetService;
    
    @PostMapping("/request")
    public ResponseEntity<ApiResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestDto request) {
        try {
            passwordResetService.requestPasswordReset(request);
            return ResponseEntity.ok(
                ApiResponse.success("비밀번호 재설정 이메일이 발송되었습니다.", null)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("이메일 발송 중 오류가 발생했습니다."));
        }
    }
    
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmDto request) {
        try {
            passwordResetService.confirmPasswordReset(request);
            return ResponseEntity.ok(
                ApiResponse.success("비밀번호가 성공적으로 변경되었습니다.", null)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("비밀번호 변경 중 오류가 발생했습니다."));
        }
    }
}
