package com.example.mate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Rating totals retained without reviewer, party, comment, or review timestamp. */
@Entity
@Table(name = "mate_anonymous_review_aggregates")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnonymousPartyReviewAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    @Column(name = "rating_sum", nullable = false)
    private Long ratingSum;

    @Column(name = "review_count", nullable = false)
    private Long reviewCount;
}
