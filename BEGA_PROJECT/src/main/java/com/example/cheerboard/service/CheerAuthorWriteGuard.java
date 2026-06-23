package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.AccountStatusUtil;
import com.example.common.exception.InvalidAuthorException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CheerAuthorWriteGuard {

    private static final String INVALID_AUTHOR_MESSAGE = "인증된 사용자의 계정이 유효하지 않습니다. 다시 로그인해 주세요.";

    private CheerAuthorWriteGuard() {
    }

    public static UserEntity resolveWriteAuthor(UserEntity me, UserRepository userRepository, EntityManager entityManager) {
        if (me == null || me.getId() == null) {
            throw new InvalidAuthorException(INVALID_AUTHOR_MESSAGE);
        }

        Long principalUserId = getAuthenticationUserId();
        if (principalUserId != null && !principalUserId.equals(me.getId())) {
            throw new InvalidAuthorException(INVALID_AUTHOR_MESSAGE);
        }

        UserEntity author = userRepository.findByIdForWrite(me.getId())
                .orElseThrow(() -> new InvalidAuthorException(INVALID_AUTHOR_MESSAGE));
        ensureAuthorRecordStillExists(author, userRepository);

        try {
            entityManager.refresh(author);
        } catch (EntityNotFoundException e) {
            throw new InvalidAuthorException(INVALID_AUTHOR_MESSAGE);
        }

        return Objects.requireNonNull(author);
    }

    public static UserEntity ensureAuthorRecordStillExists(UserEntity author, UserRepository userRepository) {
        if (author == null || author.getId() == null) {
            throw new InvalidAuthorException(INVALID_AUTHOR_MESSAGE);
        }

        Integer tokenVersion = getAuthenticationTokenVersion();
        boolean hasUsableAuthor = tokenVersion == null
                ? userRepository.lockUsableAuthorForWrite(author.getId()).isPresent()
                : userRepository.lockUsableAuthorForWriteWithTokenVersion(author.getId(), tokenVersion).isPresent();
        if (!hasUsableAuthor) {
            throw new InvalidAuthorException(INVALID_AUTHOR_MESSAGE);
        }

        UserEntity freshAuthor = userRepository.findByIdForWrite(author.getId())
                .orElseThrow(() -> new InvalidAuthorException(INVALID_AUTHOR_MESSAGE));
        if (!AccountStatusUtil.hasMatchingTokenVersion(freshAuthor, tokenVersion)
                || !freshAuthor.isEnabled()
                || !AccountStatusUtil.isAccountUsable(freshAuthor)) {
            throw new InvalidAuthorException(INVALID_AUTHOR_MESSAGE);
        }

        return Objects.requireNonNull(freshAuthor);
    }

    public static Long getAuthenticationUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String userId) {
            try {
                return Long.valueOf(userId);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Integer getAuthenticationTokenVersion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details == null) {
            return null;
        }
        if (details instanceof Integer version) {
            return version;
        }
        if (details instanceof Long version) {
            return version.intValue();
        }
        if (details instanceof Map<?, ?> map) {
            Object value = map.get("tokenVersion");
            if (value instanceof Integer version) {
                return version;
            }
            if (value instanceof Long version) {
                return version.intValue();
            }
        }
        return null;
    }
}
