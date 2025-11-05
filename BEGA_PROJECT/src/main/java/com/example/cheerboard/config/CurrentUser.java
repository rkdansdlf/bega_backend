package com.example.cheerboard.config;

import com.example.demo.dto.CustomOAuth2User;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;
import com.example.demo.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

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
            cached = userRepository.findById(userId).orElse(null);
            resolved = true;
            return cached;
        }

        String identifier = resolvePrincipal(principal);
        cached = findUser(identifier);

        resolved = true;
        return cached;
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
                return userRepository.findById(userId).orElse(null);
            } catch (NumberFormatException ignore) {
                // fall back to email lookup below
            }
        }
        return userRepository.findByEmail(identifier)
                .orElse(null);
    }
}
