package com.example.mate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "mate_search_terms_daily",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_mate_search_daily_date_term",
                        columnNames = {"search_date", "normalized_term"})
        },
        indexes = {
                @Index(
                        name = "idx_mate_search_daily_rank",
                        columnList = "search_date, search_count, last_searched_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MateSearchTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_date", nullable = false)
    private LocalDate searchDate;

    @Column(name = "normalized_term", nullable = false, length = 50)
    private String normalizedTerm;

    @Column(name = "display_term", nullable = false, length = 50)
    private String displayTerm;

    @Column(name = "search_count", nullable = false)
    private Long searchCount;

    @Column(name = "last_searched_at", nullable = false)
    private Instant lastSearchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (searchCount == null) {
            searchCount = 1L;
        }
        if (lastSearchedAt == null) {
            lastSearchedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
