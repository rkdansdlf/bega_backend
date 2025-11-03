package com.example.demo.entity;

import com.example.demo.dto.UserDto; 
import lombok.*;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate; // CreatedDate 임포트
import org.springframework.data.jpa.domain.support.AuditingEntityListener; // AuditingEntityListener 임포트
import java.time.LocalDateTime; // LocalDateTime 임포트
import java.util.Optional;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users", schema = "security")
@EntityListeners(AuditingEntityListener.class) // 생성/수정일자 자동 관리를 위한 Auditing 리스너
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자에게 보여지는 이름 (닉네임/표시 이름)
    @Column(unique = true, nullable = false)
    private String name;
    
    // 고유 이메일 (로그인 식별자 및 Spring Security Principal 역할)
    @Column(unique = true, nullable = false)
    private String email;

    // 비밀번호 (로컬 계정 전용, 소셜 계정은 null)
    private String password;
    
    // ⭐️ 추가: 프로필 이미지 URL (MyPageService에서 사용됨)
    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    // 사용자 권한 (ROLE_USER, ROLE_ADMIN 또는 팀별 Role_SS 등)
    @Column(name = "role", nullable = false)
    private String role;

    // 회원가입 시 응원팀 선택 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_team", referencedColumnName = "team_id")
    private TeamEntity favoriteTeam; // TeamEntity 객체로 매핑
    
    //가입일자
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;


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
    
    public String getFavoriteTeamId() {
        return Optional.ofNullable(this.favoriteTeam)
                       .map(TeamEntity::getTeamId)
                       .orElse(null);
    }
    
    /**
     * 엔티티 객체를 DTO 객체로 변환하는 메서드
     * (민감 정보인 비밀번호는 제외하고 전송합니다.)
     */
    public UserDto toDto() {
        return UserDto.builder()
                .id(this.id)
                .name(this.name)
                .email(this.email)
                .role(this.role)
                .favoriteTeam(this.favoriteTeam != null ? this.favoriteTeam.getTeamId() : null)
                .provider(this.provider)
                .providerId(this.providerId)
                .build();
    }
}