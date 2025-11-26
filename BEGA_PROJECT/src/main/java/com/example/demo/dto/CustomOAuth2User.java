package com.example.demo.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private final UserDto userDto;
    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String nameAttributeKey;

    public CustomOAuth2User(UserDto userDto) {
        this.userDto = userDto;
        
        // JWT flow에서는 OAuth2 attributes가 없으므로 DTO 정보를 기반으로 최소한의 Map을 생성합니다.
        this.attributes = Map.of("username", userDto.getUsername(), "role", userDto.getRole());
        
        // DTO의 Role을 기반으로 권한 설정
        this.authorities = List.of((GrantedAuthority) () -> userDto.getRole());
        this.nameAttributeKey = "username";
    }

    // 2. OAuth2 Login Flow Constructor 
    public CustomOAuth2User(UserDto userDto, Map<String, Object> attributes) {
        this.userDto = userDto;
        this.attributes = attributes;
        
        // DTO의 Role을 기반으로 권한 설정
        this.authorities = List.of((GrantedAuthority) () -> userDto.getRole());
        
        // OAuth2 attributes에서 NameAttributeKey를 결정합니다.
        this.nameAttributeKey = attributes.containsKey("sub") ? "sub" : "name"; 
    }
    
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

    public String getUsername() {
        return userDto.getUsername();
    }
    
    public UserDto getUserDto() {
        return userDto;
    }
}