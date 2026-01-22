package com.example.mate.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "parties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostid", nullable = false)
    private Long hostId; // 호스트 사용자 ID

    @Column(nullable = false, length = 50)
    private String hostName; // 호스트 이름

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BadgeType hostBadge; // 호스트 뱃지 (NEW, VERIFIED, TRUSTED)

    @Column(name = "host_profile_image_url", length = 2048)
    private String hostProfileImageUrl;

    @Column(nullable = false)
    private Double hostRating; // 호스트 평점

    @Column(nullable = false, length = 20)
    private String teamId; // 응원 팀 ID

    @Column(nullable = false)
    private LocalDate gameDate; // 경기 날짜

    @Column(nullable = false)
    private LocalTime gameTime; // 경기 시간

    @Column(nullable = false, length = 100)
    private String stadium; // 구장명

    @Column(name = "host_favorite_team", length = 20)
    private String hostFavoriteTeam; // 호스트가 응원하는 팀 ID

    @Column(nullable = false, length = 20)
    private String homeTeam; // 홈 팀 ID

    @Column(nullable = false, length = 20)
    private String awayTeam; // 원정 팀 ID

    @Column(nullable = false, length = 50)
    private String section; // 섹션 정보

    @Column(nullable = false)
    private Integer maxParticipants; // 최대 참여 인원

    @Column(nullable = false)
    private Integer currentParticipants; // 현재 참여 인원

    @Column(nullable = false, length = 1000)
    private String description; // 파티 소개

    @Column(nullable = false)
    private Boolean ticketVerified; // 예매내역 인증 여부

    @Column(length = 500)
    private String ticketImageUrl; // 예매내역 이미지 URL

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PartyStatus status; // 파티 상태

    @Column
    private Integer price; // 티켓 판매가 (판매 모드일 때)

    @Column(name = "ticketprice")
    private Integer ticketPrice; // 티켓 가격(1인당)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (currentParticipants == null) {
            currentParticipants = 1; // 호스트 포함
        }
        if (ticketVerified == null) {
            ticketVerified = false;
        }
        if (hostRating == null) {
            hostRating = 5.0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Enum 정의
    public enum BadgeType {
        NEW("새 회원"),
        VERIFIED("인증 회원"),
        TRUSTED("신뢰 회원");

        private final String description;

        BadgeType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum PartyStatus {
        PENDING("모집 중"),
        MATCHED("매칭 성공"),
        FAILED("매칭 실패"),
        SELLING("티켓 판매"),
        SOLD("판매 완료"),
        CHECKED_IN("체크인 완료"),
        COMPLETED("관람 완료");

        private final String description;

        PartyStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}