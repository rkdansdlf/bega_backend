package com.example.auth.controller;

import com.example.auth.dto.OAuthEmailChallengeConfirmDto;
import com.example.auth.dto.OAuthEmailChallengeSubmitDto;
import com.example.auth.service.OAuthEmailChallengeService;
import com.example.common.dto.ApiResponse;
import com.example.common.ratelimit.RateLimit;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/oauth2/email-challenge")
@RequiredArgsConstructor
public class OAuthEmailChallengeController {

    private final OAuthEmailChallengeService challengeService;

    @GetMapping("/{challengeId}")
    public ResponseEntity<ApiResponse<OAuthEmailChallengeService.ChallengeStatus>> status(
            @PathVariable String challengeId) {
        return ResponseEntity.ok(ApiResponse.success(
                "이메일 확인 상태를 조회했습니다.",
                challengeService.status(challengeId)));
    }

    @PostMapping("/{challengeId}/email")
    @RateLimit(limit = 3, window = 3600, key = "auth:oauth-email-challenge-email", failClosed = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitEmail(
            @PathVariable String challengeId,
            @Valid @RequestBody OAuthEmailChallengeSubmitDto request) {
        challengeService.submitEmail(challengeId, request.email());
        return sent(challengeId);
    }

    @PostMapping("/{challengeId}/resend")
    @RateLimit(limit = 3, window = 3600, key = "auth:oauth-email-challenge-resend", failClosed = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> resend(@PathVariable String challengeId) {
        challengeService.resend(challengeId);
        return sent(challengeId);
    }

    @PostMapping("/confirm")
    @RateLimit(limit = 10, window = 900, key = "auth:oauth-email-challenge-confirm", failClosed = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirm(
            @Valid @RequestBody OAuthEmailChallengeConfirmDto request) {
        return confirmed(challengeService.confirm(request.token()));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> confirmed(Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                "이메일 확인이 완료되었습니다. 소셜 로그인을 다시 진행해주세요.",
                Map.of("confirmed", true, "userId", userId)));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> sent(String challengeId) {
        return ResponseEntity.ok(ApiResponse.success(
                "이메일 확인 링크를 발송했습니다.",
                Map.of("challengeId", challengeId, "status", "EMAIL_SENT")));
    }
}
