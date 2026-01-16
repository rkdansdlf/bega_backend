package com.example.demo.entity;

import com.example.demo.dto.UserDto;
import lombok.*;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
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

    // 닉네임/표시 이름
    @Column(nullable = false)
    private String name;

    // 로그인 식별자 및 Spring Security Principal 역할
    @Column(unique = true, nullable = false)
    private String email;

    // 비밀번호
    private String password;

    // 프로필 이미지 URL (MyPageService에서 사용됨)
    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    // 사용자 권한
    @Column(name = "role", nullable = false)
    private String role;

    // 회원가입 시 응원팀 선택 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_team", referencedColumnName = "team_id")
    private TeamEntity favoriteTeam; // TeamEntity 객체로 매핑

    // 가입일자
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // OAuth2 제공자 (LOCAL, GOOGLE, KAKAO 등) - Deprecated (Use providers list)
    private String provider;

    // OAuth2 제공자의 고유ID (소셜 계정 연동 시 사용) - Deprecated (Use providers list)
    @Column(name = "providerid")
    private String providerId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<UserProvider> providers = new java.util.ArrayList<>();

    // JWT에 넣기 위해 단일 권한 키 문자열을 반환하는 메서드
    public String getRoleKey() {
        return this.role;
    }

    // 역할을 설정하는 메서드 (Role Enum의 getKey())
    public void setRole(String roleKey) {
        this.role = roleKey;
    }

    // 이메일을 설정하는 메서드
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
     * 관리자인지 확인하는 헬퍼 메서드
     */
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(this.role);
    }

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