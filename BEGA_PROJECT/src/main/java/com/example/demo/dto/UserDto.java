package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO는 프론트엔드 JSON 필드(name, email, password, favoriteTeam)를 직접 반영합니다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    
    // 프론트엔드에서 전달되는 Display Name (사용자에게 보이는 이름)
    private String name; 
    
    // 이메일 (로그인 ID)
    private String email; 
    
    // 비밀번호
    private String password;
    
    // 선호 팀
    private String favoriteTeam;
    
    
    // Spring Security의 UserDetails 인터페이스 호환성을 위해 getUsername()을 유지하고 email을 반환합니다.
    public String getUsername() {
        return this.email;
    }
    
    // 소셜 로그인 관련 필드 (일반 회원가입 시에는 null 또는 LOCAL)
    private String provider;
    private String providerId;
    
    // 권한 (백엔드 내부용)
    private String role;
}