package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_providers", schema = "security", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "provider", "providerid" }),
        @UniqueConstraint(columnNames = { "user_id", "provider" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 20)
    private String provider; // google, kakao

    @Column(name = "providerid", nullable = false)
    private String providerId;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @PrePersist
    protected void onCreate() {
        connectedAt = LocalDateTime.now();
    }
}
