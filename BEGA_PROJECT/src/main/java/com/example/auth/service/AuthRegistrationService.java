package com.example.auth.service;

import com.example.auth.dto.SignupDto;
import com.example.auth.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthRegistrationService {

    private final UserService userService;
    private final PolicyConsentService policyConsentService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public UserEntity register(SignupDto signupDto, String clientIp, String userAgent) {
        policyConsentService.validateRequiredConsents(signupDto.getPolicyConsents());

        UserEntity createdUser = userService.saveUser(signupDto);
        policyConsentService.recordRequiredConsents(
                createdUser.getId(),
                signupDto.getPolicyConsents(),
                "signup",
                clientIp,
                userAgent);
        emailVerificationService.issueForSignup(createdUser);

        return createdUser;
    }

    /** 로그인에서 이메일 인증을 강제하는지(app.auth.email-verification.required). */
    public boolean isEmailVerificationRequired() {
        return emailVerificationService.isVerificationRequired();
    }
}

