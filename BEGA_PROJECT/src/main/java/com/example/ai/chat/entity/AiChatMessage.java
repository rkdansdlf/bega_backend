package com.example.ai.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@Table(name = "ai_chat_message")
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AiChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private AiChatMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiChatMessageStatus status;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "cached")
    private Boolean cached;

    @Column(name = "intent", length = 100)
    private String intent;

    @Column(name = "strategy", length = 100)
    private String strategy;

    @Column(name = "finish_reason", length = 50)
    private String finishReason;

    @Column(name = "cancelled", nullable = false)
    @Builder.Default
    private boolean cancelled = false;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "planner_mode", length = 50)
    private String plannerMode;

    @Column(name = "planner_cache_hit")
    private Boolean plannerCacheHit;

    @Column(name = "tool_execution_mode", length = 50)
    private String toolExecutionMode;

    @Column(name = "fallback_reason", length = 100)
    private String fallbackReason;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "metadata_json")
    @Builder.Default
    private String metadataJson = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "citations_json")
    @Builder.Default
    private String citationsJson = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "tool_calls_json")
    @Builder.Default
    private String toolCallsJson = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (metadataJson == null) {
            metadataJson = "";
        }
        if (citationsJson == null) {
            citationsJson = "";
        }
        if (toolCallsJson == null) {
            toolCallsJson = "";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
