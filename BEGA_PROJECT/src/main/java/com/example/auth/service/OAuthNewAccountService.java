package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.BadRequestBusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthNewAccountService {

    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;

    @Transactional
    public UserEntity create(String email, String name, String provider, String providerId, String profileImageUrl) {
        if (userProviderRepository.findByProviderAndProviderIdForUpdate(provider, providerId).isPresent()) {
            throw new BadRequestBusinessException("OAUTH_ACCOUNT_ALREADY_EXISTS", "이미 가입된 소셜 계정입니다.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestBusinessException("MANUAL_LINK_REQUIRED", "기존 계정에서 소셜 계정을 연동해주세요.");
        }

        UserEntity user = UserEntity.builder()
                .email(email)
                .name(name != null && !name.isBlank() ? name : "소셜 사용자")
                .password(null)
                .profileImageUrl(profileImageUrl)
                .role("ROLE_USER")
                .provider(provider)
                .providerId(providerId)
                .favoriteTeam(null)
                .uniqueId(UUID.randomUUID())
                .handle(generateRandomHandle())
                .build();
        try {
            UserEntity saved = userRepository.saveAndFlush(user);
            userProviderRepository.saveAndFlush(UserProvider.builder()
                    .user(saved)
                    .provider(provider)
                    .providerId(providerId)
                    .email(email)
                    .build());
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestBusinessException("OAUTH_ACCOUNT_CONFLICT", "소셜 계정 생성 중 충돌이 발생했습니다.");
        }
    }

    private String generateRandomHandle() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
