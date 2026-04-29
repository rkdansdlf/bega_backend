package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.RefreshTokenRevokeFailedException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRevocationService {

    private final UserRepository userRepository;
    private final RefreshRepository refreshRepository;

    public record RevokedRefreshSessions(Long userId, String email) {
    }

    @Transactional
    public RevokedRefreshSessions revokeAllSessionsAfterReuse(Long userId) {
        if (userId == null) {
            throw new RefreshTokenRevokeFailedException();
        }

        try {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(RefreshTokenRevokeFailedException::new);
            if (user.getId() == null || !userId.equals(user.getId())) {
                throw new RefreshTokenRevokeFailedException();
            }
            String email = user.getEmail();
            if (!StringUtils.hasText(email)) {
                throw new RefreshTokenRevokeFailedException();
            }

            user.setTokenVersion(Optional.ofNullable(user.getTokenVersion()).orElse(0) + 1);
            userRepository.save(user);
            refreshRepository.deleteByEmail(email);
            return new RevokedRefreshSessions(userId, email);
        } catch (RefreshTokenRevokeFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to revoke refresh sessions after reuse detection: userId={}", userId, e);
            throw new RefreshTokenRevokeFailedException(e);
        }
    }
}
