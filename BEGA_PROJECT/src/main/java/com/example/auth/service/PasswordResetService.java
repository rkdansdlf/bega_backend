package com.example.auth.service;

import com.example.auth.dto.PasswordResetConfirmDto;
import com.example.auth.dto.PasswordResetRequestDto;
import com.example.auth.entity.PasswordResetToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RefreshRepository refreshRepository;
    private final AuthSecurityMonitoringService authSecurityMonitoringService;

    @Transactional
    public void requestPasswordReset(PasswordResetRequestDto request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        Optional<UserEntity> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            authSecurityMonitoringService.recordPasswordResetSuppressed();
            log.info("Password reset request suppressed for unknown email");
            return;
        }

        UserEntity user = userOpt.get();

        // 소셜 로그인 전용 계정 체크
        if (user.getPassword() == null) {
            authSecurityMonitoringService.recordPasswordResetSuppressed();
            log.info("Password reset request suppressed for social-only account: userId={}", user.getId());
            return;
        }

        try {
            // 기존 토큰 삭제
            tokenRepository.deleteByUserId(Objects.requireNonNull(user.getId()));

            // 새 토큰 생성 (30분 유효)
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiryDate(LocalDateTime.now().plusMinutes(30))
                    .used(false)
                    .build();

            tokenRepository.save(Objects.requireNonNull(resetToken));

            // 이메일 발송
            emailService.sendPasswordResetEmail(Objects.requireNonNull(user.getEmail()), token, request.getRedirect());
        } catch (RuntimeException e) {
            authSecurityMonitoringService.recordPasswordResetSuppressed();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warn("Password reset request suppressed after internal failure: userId={}", user.getId(), e);
        }
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmDto request) {

        // 비밀번호 일치 확인
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 비밀번호 길이 확인
        if (request.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("비밀번호는 최소 8자 이상이어야 합니다.");
        }

        // 토큰 조회
        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> {
                    return new IllegalArgumentException("유효하지 않은 토큰입니다.");
                });

        // 토큰 유효성 검증
        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("이미 사용된 토큰입니다.");
        }

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("만료된 토큰입니다. 다시 요청해주세요.");
        }

        // 비밀번호 변경
        UserEntity user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(Optional.ofNullable(user.getTokenVersion()).orElse(0) + 1);
        userRepository.save(Objects.requireNonNull(user));
        refreshRepository.deleteByEmail(user.getEmail());

        // 토큰 사용 처리
        resetToken.setUsed(true);
        tokenRepository.save(Objects.requireNonNull(resetToken));
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
