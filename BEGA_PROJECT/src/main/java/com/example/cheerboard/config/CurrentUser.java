package com.example.cheerboard.config;

import com.example.auth.dto.CustomOAuth2User;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Map;
import java.util.Objects;

@Component
@RequestScope
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    private UserEntity cached;
    private boolean resolved;

    public UserEntity get() {
        UserEntity user = getOrNull();
        if (user == null) {
            throw new AuthenticationCredentialsNotFoundException("인증된 사용자만 이용할 수 있습니다.");
        }
        return user;
    }

    public UserEntity getOrNull() {
        if (resolved) {
            if (cached == null) {
                return null;
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Integer tokenVersionFromAuthentication = resolveTokenVersionFromAuthentication(authentication);
            cached = refreshCurrentUser(cached.getId(), tokenVersionFromAuthentication);
            return cached;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getPrincipal() == null
            || "anonymousUser".equals(authentication.getPrincipal())) {
            resolved = true;
            cached = null;
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Long userId) {
            Integer tokenVersionFromAuthentication = resolveTokenVersionFromAuthentication(authentication);
            cached = refreshCurrentUser(userId, tokenVersionFromAuthentication);
            resolved = true;
            return cached;
        }

        String identifier = resolvePrincipal(principal);
        cached = findUser(identifier);
        if (cached != null && cached.getId() != null) {
            Integer tokenVersionFromAuthentication = resolveTokenVersionFromAuthentication(authentication);
            cached = refreshCurrentUser(cached.getId(), tokenVersionFromAuthentication);
        }

        resolved = true;
        return cached;
    }

    private UserEntity refreshCurrentUser(UserEntity user) {
        if (user == null || user.getId() == null) {
            return null;
        }

        return refreshCurrentUser(user.getId(), null);
    }

    private UserEntity refreshCurrentUser(Long userId, Integer expectedTokenVersion) {
        if (userId == null) {
            return null;
        }

        boolean usableAuthor = expectedTokenVersion == null
                ? userRepository.existsUsableAuthorById(userId)
                : userRepository.existsUsableAuthorByIdAndTokenVersion(userId, expectedTokenVersion);
        if (!usableAuthor) {
            return null;
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        return isAuthoritativeUser(user, expectedTokenVersion) ? user : null;
    }

    private UserEntity refreshCurrentUser(Long userId) {
        if (userId == null) {
            return null;
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        return isAuthoritativeUser(user) ? user : null;
    }

    private boolean isAuthoritativeUser(UserEntity user) {
        return isAuthoritativeUser(user, null);
    }

    private boolean isAuthoritativeUser(UserEntity user, Integer expectedTokenVersion) {
        if (user == null || user.getId() == null) {
            return false;
        }

        if (!user.isEnabled() || !isAccountUsable(user)) {
            return false;
        }

        if (expectedTokenVersion == null) {
            return (user.getTokenVersion() == null || user.getTokenVersion() == 0);
        }

        int currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return currentTokenVersion == expectedTokenVersion;
    }

    private Integer resolveTokenVersionFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object details = authentication.getDetails();
        if (details == null) {
            return null;
        }

        if (details instanceof Integer tokenVersion) {
            return tokenVersion;
        }
        if (details instanceof Long longValue) {
            return longValue.intValue();
        }
        if (details instanceof Number number) {
            return number.intValue();
        }
        if (details instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignore) {
                // skip
            }
        }
        if (details instanceof Map<?, ?> map) {
            Object legacyTokenVersionValue = map.get("token_version");
            if (legacyTokenVersionValue instanceof Integer version) {
                return version;
            }
            if (legacyTokenVersionValue instanceof Long version) {
                return version.intValue();
            }
            if (legacyTokenVersionValue instanceof Number version) {
                return version.intValue();
            }
            if (legacyTokenVersionValue instanceof String version) {
                try {
                    return Integer.parseInt(version);
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }

            Object tokenVersionValue = map.get("tokenVersion");
            if (tokenVersionValue instanceof Integer version) {
                return version;
            }
            if (tokenVersionValue instanceof Long version) {
                return version.intValue();
            }
            if (tokenVersionValue instanceof Number version) {
                return version.intValue();
            }
            if (tokenVersionValue instanceof String version) {
                try {
                    return Integer.parseInt(version);
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }
        }

        return null;
    }

    private boolean isAccountUsable(UserEntity user) {
        if (!user.isLocked()) {
            return true;
        }

        if (user.getLockExpiresAt() == null) {
            return false;
        }

        return user.getLockExpiresAt().isBefore(java.time.LocalDateTime.now());
    }

    private String resolvePrincipal(Object principal) {
        if (principal instanceof CustomOAuth2User oAuth2User) {
            var dto = oAuth2User.getUserDto();
            if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
                return dto.getEmail();
            }
            return dto.getUsername();
        }
        if (principal instanceof CustomUserDetails details) {
            return details.getUsername();
        }
        if (principal instanceof UserDetails springUser) {
            return springUser.getUsername();
        }
        return principal.toString();
    }

    private UserEntity findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        if (identifier.chars().allMatch(Character::isDigit)) {
            try {
                Long userId = Long.valueOf(identifier);
                return userRepository.findById(Objects.requireNonNull(userId)).orElse(null);
            } catch (NumberFormatException ignore) {
                // fall back to email lookup below
            }
        }
        return userRepository.findByEmail(identifier)
                .orElse(null);
    }
}
