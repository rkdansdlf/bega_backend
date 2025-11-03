package com.example.demo.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Custom implementation of OAuth2User to store user details, using the application's
 * UserDto as the core authenticated principal data.
 */
public class CustomOAuth2User implements OAuth2User {

    // 내부 상태는 JWT와 OAuth2 모두에서 사용하기 쉬운 UserDto를 기반으로 합니다.
    private final UserDto userDto;
    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String nameAttributeKey;

    // 1. NEW: JWT Filter Flow Constructor (단일 인자: UserDto)
    // 로그에서 발생한 "The constructor CustomOAuth2User(UserDto) is undefined" 오류 해결
    public CustomOAuth2User(UserDto userDto) {
        this.userDto = userDto;
        
        // JWT flow에서는 OAuth2 attributes가 없으므로 DTO 정보를 기반으로 최소한의 Map을 생성합니다.
        this.attributes = Map.of("username", userDto.getUsername(), "role", userDto.getRole());
        
        // DTO의 Role을 기반으로 권한 설정
        this.authorities = List.of((GrantedAuthority) () -> userDto.getRole());
        this.nameAttributeKey = "username";
    }

    // 2. OAuth2 Login Flow Constructor (두 인자: UserDto + Attributes)
    // CustomOAuth2UserService에서 발생하는 두 인자 생성자 호출 오류 해결 (UserEntity 대신 UserDto 사용)
    public CustomOAuth2User(UserDto userDto, Map<String, Object> attributes) {
        this.userDto = userDto;
        this.attributes = attributes;
        
        // DTO의 Role을 기반으로 권한 설정
        this.authorities = List.of((GrantedAuthority) () -> userDto.getRole());
        
        // OAuth2 attributes에서 NameAttributeKey를 결정합니다.
        this.nameAttributeKey = attributes.containsKey("sub") ? "sub" : "name"; 
    }
    
    // --- OAuth2User 인터페이스 필수 구현 메서드 ---
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        // Name Attribute Key를 사용하거나, DTO의 username으로 대체합니다.
        return this.attributes.getOrDefault(this.nameAttributeKey, userDto.getUsername()).toString();
    }

    // --- 추가 getter 메서드 ---
    public String getUsername() {
        return userDto.getUsername();
    }
    
    public UserDto getUserDto() {
        return userDto;
    }
}