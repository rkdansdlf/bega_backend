package com.example.mate.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "party_reviews",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"party_id", "reviewer_id", "reviewee_id"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId; // 파티 ID

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId; // 리뷰 작성자 ID

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId; // 리뷰 대상자 ID

    @Column(nullable = false)
    private Integer rating; // 평점 (1-5)

    @Column(length = 200)
    private String comment; // 코멘트 (최대 200자)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
