package com.example.mate.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "party_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partyid", nullable = false)
    private Long partyId; // 파티 ID

    @Column(name = "applicantid", nullable = false)
    private Long applicantId; // 신청자 사용자 ID

    @Column(nullable = false, length = 50)
    private String applicantName; // 신청자 이름

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Party.BadgeType applicantBadge; // 신청자 뱃지

    @Column(nullable = false)
    private Double applicantRating; // 신청자 평점

    @Column(nullable = false, length = 500)
    private String message; // 신청 메시지

    @Column(nullable = false)
    private Integer depositAmount; // 보증금 금액

    @Column(nullable = false)
    private Boolean isPaid; // 결제 완료 여부

    @Column(nullable = false)
    private Boolean isApproved; // 승인 여부

    @Column(nullable = false)
    private Boolean isRejected; // 거절 여부

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentType paymentType; // 결제 방식 (DEPOSIT, FULL)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime approvedAt; // 승인 시간

    @Column
    private LocalDateTime rejectedAt; // 거절 시간

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isPaid == null) {
            isPaid = false;
        }
        if (isApproved == null) {
            isApproved = false;
        }
        if (isRejected == null) {
            isRejected = false;
        }
        if (applicantRating == null) {
            applicantRating = 5.0;
        }
    }

    // Enum 정의
    public enum PaymentType {
        DEPOSIT("보증금"),
        FULL("전액");

        private final String description;

        PaymentType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}