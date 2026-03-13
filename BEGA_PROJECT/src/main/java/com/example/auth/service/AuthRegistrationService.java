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

        return createdUser;
    }
}

