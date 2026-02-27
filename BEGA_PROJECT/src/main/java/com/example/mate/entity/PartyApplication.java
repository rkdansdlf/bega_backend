package com.example.mate.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Entity
@Table(name = "party_applications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pa_order_id", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_pa_payment_key", columnNames = "payment_key")
})
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

    @Column(name = "applicant_name", nullable = false, length = 50)
    private String applicantName; // 신청자 이름

    @Column(name = "applicant_badge", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Party.BadgeType applicantBadge; // 신청자 뱃지

    @Column(name = "applicant_rating", nullable = false)
    private Double applicantRating; // 신청자 평점

    @Column(nullable = false, length = 500)
    private String message; // 신청 메시지

    @Column(name = "deposit_amount", nullable = false)
    private Integer depositAmount; // 보증금 금액

    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid; // 결제 완료 여부

    @Column(name = "is_approved", nullable = false)
    private Boolean isApproved; // 승인 여부

    @Column(name = "is_rejected", nullable = false)
    private Boolean isRejected; // 거절 여부

    @Column(name = "payment_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentType paymentType; // 결제 방식 (DEPOSIT, FULL)

    @Column(name = "ticket_verified")
    private Boolean ticketVerified; // 티켓 인증 여부

    @Column(name = "ticket_image_url", length = 500)
    private String ticketImageUrl; // 인증 티켓 이미지 URL

    @Column(name = "payment_key", length = 200)
    private String paymentKey; // Toss Payments paymentKey

    @Column(name = "order_id", length = 100)
    private String orderId; // 결제 주문 ID (orderId)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt; // 승인 시간

    @Column(name = "rejected_at")
    private Instant rejectedAt; // 거절 시간

    @Column(name = "response_deadline")
    private Instant responseDeadline; // 응답 기한 (48시간)

    @Column(name = "last_read_chat_at")
    private Instant lastReadChatAt; // 신청자 마지막 채팅 확인 시간

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        lastReadChatAt = Instant.now();
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
        if (ticketVerified == null) {
            ticketVerified = false;
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
