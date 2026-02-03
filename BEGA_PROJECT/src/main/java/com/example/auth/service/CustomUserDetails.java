package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security UserDetails 구현체
 * [Security Fix] 계정 상태 검증 로직 구현
 */
public class CustomUserDetails implements UserDetails {

    private final UserEntity userEntity;

    public CustomUserDetails(UserEntity userEntity) {
        this.userEntity = userEntity;
    }

    public UserEntity getUserEntity() {
        return userEntity;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Role을 SimpleGrantedAuthority로 변환하여 반환
        return Collections.singletonList((GrantedAuthority) () -> userEntity.getRole());
    }

    @Override
    public String getPassword() {
        return userEntity.getPassword();
    }

    @Override
    public String getUsername() {
        return userEntity.getEmail();
    }

    public String getEmail() {
        return userEntity.getEmail();
    }

    public Long getId() {
        return userEntity.getId();
    }

    /**
     * 계정 만료 여부 (현재 미사용 - 향후 확장 가능)
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 계정 잠금 여부 확인
     * - locked 필드가 true이고 lockExpiresAt이 미래이면 잠금 상태
     * - lockExpiresAt이 과거이면 자동 해제
     */
    @Override
    public boolean isAccountNonLocked() {
        if (!userEntity.isLocked()) {
            return true; // 잠금 상태가 아님
        }

        // 잠금 해제 시간 확인
        LocalDateTime lockExpiresAt = userEntity.getLockExpiresAt();
        if (lockExpiresAt != null && lockExpiresAt.isBefore(LocalDateTime.now())) {
            return true; // 잠금 기간 만료 - 로그인 허용
        }

        return false; // 계정 잠금 중
    }

    /**
     * 비밀번호 만료 여부 (현재 미사용 - 향후 확장 가능)
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 계정 활성화 여부
     * - enabled 필드가 false이면 로그인 불가
     */
    @Override
    public boolean isEnabled() {
        return userEntity.isEnabled();
    }
}
