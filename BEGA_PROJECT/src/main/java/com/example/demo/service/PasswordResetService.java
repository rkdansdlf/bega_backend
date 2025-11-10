package com.example.demo.service;

import com.example.demo.dto.PasswordResetConfirmDto;
import com.example.demo.dto.PasswordResetRequestDto;
import com.example.demo.entity.PasswordResetToken;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.PasswordResetTokenRepository;
import com.example.demo.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    
    @Transactional
    public void requestPasswordReset(PasswordResetRequestDto request) {
        // 사용자 조회
        UserEntity user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 가입된 사용자를 찾을 수 없습니다."));
        
        // 소셜 로그인 전용 계정 체크
        if (user.getPassword() == null) {
            throw new IllegalArgumentException("소셜 로그인 계정은 비밀번호 재설정을 할 수 없습니다.");
        }
        
        // 기존 토큰 삭제
        tokenRepository.deleteByUserId(user.getId());
        
        // 새 토큰 생성 (30분 유효)
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
            .token(token)
            .user(user)
            .expiryDate(LocalDateTime.now().plusMinutes(30))
            .used(false)
            .build();
        
        tokenRepository.save(resetToken);
        
        // 이메일 발송
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }
    
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmDto request) {
        System.out.println("받은 토큰: " + request.getToken()); // 디버깅 로그
        System.out.println("새 비밀번호 길이: " + request.getNewPassword().length());
        
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
                System.err.println("토큰을 찾을 수 없음: " + request.getToken()); // 에러 로그
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
        userRepository.save(user);
        
        // 토큰 사용 처리
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }
}
