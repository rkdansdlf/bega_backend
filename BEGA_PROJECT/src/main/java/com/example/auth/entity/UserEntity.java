package com.example.auth.entity;

import com.example.auth.dto.UserDto;
import com.example.kbo.entity.TeamEntity;
import lombok.*;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.Optional;

import java.util.UUID;

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

    // 내부 보안 식별자 (무작위 UUID)
    @Column(name = "unique_id", unique = true, nullable = false)
    private UUID uniqueId;

    // 사용자 아이디 (@handle)
    @Column(unique = true, nullable = false, length = 15)
    private String handle;

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

    // 자기소개
    @Column(name = "bio", length = 500)
    private String bio;

    // 비공개 계정 여부
    @Column(name = "private_account", nullable = false)
    @Builder.Default
    private boolean privateAccount = false;

    // 회원가입 시 응원팀 선택 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_team", referencedColumnName = "team_id")
    private TeamEntity favoriteTeam; // TeamEntity 객체로 매핑

    // 응원 포인트 (좋아요 받으면 증가, 응원 배틀에 사용)
    @Column(name = "cheer_points", nullable = false)
    @Builder.Default
    private Integer cheerPoints = 0;

    // 마지막으로 출석 보너스를 받은 날짜 (매일 1회 지급용)
    @Column(name = "last_bonus_date")
    private java.time.LocalDate lastBonusDate;

    // 마지막 로그인 일시
    @Column(name = "last_login_date")
    private Instant lastLoginDate;

    // 가입일자
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    // OAuth2 제공자 (LOCAL, GOOGLE, KAKAO 등) - Deprecated (Use providers list)
    private String provider;

    // OAuth2 제공자의 고유ID (소셜 계정 연동 시 사용) - Deprecated (Use providers list)
    @Column(name = "providerid")
    private String providerId;

    @Builder.Default
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
     * ADMIN 또는 SUPER_ADMIN 역할을 가진 경우 true 반환
     */
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(this.role) || "ROLE_SUPER_ADMIN".equals(this.role);
    }

    /**
     * 최고 관리자인지 확인하는 헬퍼 메서드
     * SUPER_ADMIN만 true 반환
     */
    public boolean isSuperAdmin() {
        return "ROLE_SUPER_ADMIN".equals(this.role);
    }

    public UserDto toDto() {
        return UserDto.builder()
                .id(this.id)
                .name(this.name)
                .handle(this.handle)
                .email(this.email)
                .role(this.role)
                .favoriteTeam(this.favoriteTeam != null ? this.favoriteTeam.getTeamId() : null)
                .provider(this.provider)
                .providerId(this.providerId)
                .build();
    }

    public void addCheerPoints(int points) {
        this.cheerPoints = (this.cheerPoints == null ? 0 : this.cheerPoints) + points;
    }

    public void deductCheerPoints(int points) {
        int currentPoints = (this.cheerPoints == null ? 0 : this.cheerPoints);
        if (currentPoints < points) {
            throw new IllegalStateException("응원 포인트가 부족합니다.");
        }
        this.cheerPoints = currentPoints - points;
    }
}