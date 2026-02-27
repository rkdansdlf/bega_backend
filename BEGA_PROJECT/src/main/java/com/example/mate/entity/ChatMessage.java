package com.example.mate.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId; // 파티 ID

    @Column(name = "sender_id", nullable = false)
    private Long senderId; // 발신자 사용자 ID

    @Column(name = "sender_name", nullable = false, length = 50)
    private String senderName; // 발신자 이름

    @Column(name = "message", nullable = false, length = 1000)
    private String message; // 메시지 내용

    @Column(name = "image_url", length = 2048)
    private String imageUrl; // 이미지 첨부 URL (선택)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
