package com.example.demo.service;

import com.example.demo.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Collections;

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
        // 단일 Role을 SimpleGrantedAuthority로 변환하여 반환
        return Collections.singletonList((GrantedAuthority) () -> userEntity.getRole());
    }

    @Override
    public String getPassword() {
        return userEntity.getPassword();
    }

    /**
     * Spring Security Principal의 식별자(username)를 반환합니다.
     * UserEntity에서 username 필드를 제거하고 email을 사용하므로, email을 반환합니다.
     */
    @Override
    public String getUsername() {
        return userEntity.getEmail(); 
    }
    
    /**
     * JWT 발행 등에 사용할 수 있도록 UserEntity의 Email을 직접 반환하는 메서드입니다.
     */
    public String getEmail() {
        return userEntity.getEmail();
    }
    
    // 계정 만료 여부
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠금 여부
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 비밀번호 만료 여부
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 계정 활성화 여부
    @Override
    public boolean isEnabled() {
        return true;
    }
}
