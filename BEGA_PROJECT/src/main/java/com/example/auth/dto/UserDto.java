package com.example.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    
    private String name; 
    
    private String email; 
    
    private String password;
    
    private String favoriteTeam;
    
    public String getUsername() {
        return this.email;
    }
    
    // 소셜 로그인 관련 필드 (일반 회원가입 시 LOCAL)
    private String provider;
    private String providerId;
    
    // 권한
    private String role;
}