package com.example.demo.entity;

import com.example.demo.dto.UserDto; // 🚨 toDto() 사용을 위해 임포트

import lombok.*;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users", schema = "security")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 식별을 위한 고유 이름 (로그인 ID 또는 실제 이름으로 사용)
    @Column(unique = true, nullable = false)
    private String name;
    
    // (선택적) 사용자 이름 또는 별명
    private String username;
    
    // 고유 이메일 (로그인 시 사용되는 식별자)
    @Column(unique = true, nullable = false)
    private String email;

    // 비밀번호 (로컬 계정 전용, 소셜 계정은 null)
    private String password;

    // 사용자 권한 (ROLE_USER, ROLE_ADMIN 또는 팀별 Role_SS 등)
    private String role;

    // 회원가입 시 응원팀 선택 정보
    private String favoriteTeam;

    // OAuth2 제공자 (LOCAL, GOOGLE, KAKAO 등)
    private String provider;
    
    // OAuth2 제공자의 사용자 고유 ID (소셜 계정 연동 시 사용)
    private String providerId;

    // JWT에 넣기 위해 단일 권한 키 문자열을 반환하는 메서드
    public String getRoleKey() {
        return this.role;
    }

    // 역할을 설정하는 메서드 (Role Enum의 getKey() 결과인 String을 받습니다.)
    public void setRole(String roleKey) {
        this.role = roleKey;
    }
    
    // 이메일을 설정하는 메서드 (Lombok Setter 외에 명시적 정의)
    public void setEmail(String email) {
    	this.email = email;
    }

    // OAuth2 사용자인지 여부를 확인하는 헬퍼 메서드
    public boolean isOAuth2User() {
        return provider != null && !"LOCAL".equals(provider);
    }
    
    /**
     * 엔티티 객체를 DTO 객체로 변환하는 메서드
     * (민감 정보인 비밀번호는 제외하고 전송합니다.)
     */
    public UserDto toDto() {
        return UserDto.builder()
                .id(this.id)
                .username(this.username)
                .name(this.name)
                .email(this.email)
                .role(this.role)
                .favoriteTeam(this.favoriteTeam)
                .provider(this.provider)
                .providerId(this.providerId)
                .build();
    }
}