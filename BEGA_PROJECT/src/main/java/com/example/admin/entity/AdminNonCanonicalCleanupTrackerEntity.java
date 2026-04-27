package com.example.admin.entity;

import com.example.common.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "admin_non_canonical_cleanup_trackers", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_admin_non_canonical_cleanup_tracker_range",
                columnNames = {"start_date", "end_date"})
}, indexes = {
        @Index(
                name = "idx_admin_non_canonical_cleanup_trackers_updated_at",
                columnList = "updated_at")
})
public class AdminNonCanonicalCleanupTrackerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "ticket_url", length = 500)
    private String ticketUrl;

    @Column(length = 120)
    private String assignee;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "note_text")
    private String note;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_admin_id")
    private Long updatedByAdminId;

    @Convert(converter = StringListJsonConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "game_ids_json", nullable = false)
    @Builder.Default
    private List<String> gameIds = new ArrayList<>();

    @PrePersist
    void onPersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (gameIds == null) {
            gameIds = new ArrayList<>();
        }
    }

    public void applyUpdate(
            String ticketUrl,
            String assignee,
            String status,
            String note,
            LocalDateTime updatedAt,
            Long updatedByAdminId,
            List<String> gameIds
    ) {
        this.ticketUrl = ticketUrl;
        this.assignee = assignee;
        this.status = status;
        this.note = note;
        this.updatedAt = updatedAt;
        this.updatedByAdminId = updatedByAdminId;
        this.gameIds = gameIds == null ? new ArrayList<>() : new ArrayList<>(gameIds);
    }
}
