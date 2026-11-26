package com.example.auth.controller;

import com.example.auth.dto.EmailVerificationConfirmDto;
import com.example.auth.dto.EmailVerificationResendDto;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.EmailVerificationService;
import com.example.common.dto.ApiResponse;
import com.example.common.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 회원가입 인증 (Issue #48). 인증 전 사용자는 로그인할 수 없으므로 두 엔드포인트 모두 공개다.
 */
@Tag(name = "인증", description = "회원가입 이메일 인증")
@RestController
@RequestMapping("/api/auth/email-verification")
@RequiredArgsConstructor
public class EmailVerificationController {

    static final String RESEND_MESSAGE =
            "입력한 이메일로 인증이 필요한 계정이 있다면 인증 메일을 다시 보냈습니다.";

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/confirm")
    @RateLimit(limit = 10, window = 900, key = "auth:email-verification-confirm", failClosed = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationConfirmDto request) {
        UserEntity user = emailVerificationService.confirm(request.token());
        return ResponseEntity.ok(ApiResponse.success(
                "이메일 인증이 완료되었습니다. 로그인해주세요.",
                Map.of("verified", true, "email", user.getEmail())));
    }

    @PostMapping("/resend")
    @RateLimit(limit = 5, window = 3600, key = "auth:email-verification-resend", failClosed = true)
    public ResponseEntity<ApiResponse<Void>> resendEmailVerification(@Valid @RequestBody EmailVerificationResendDto request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.ok(ApiResponse.success(RESEND_MESSAGE, null));
    }
}
