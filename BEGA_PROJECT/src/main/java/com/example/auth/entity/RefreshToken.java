package com.example.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토큰 소유자를 식별용 이메일
    private String email;

    // 리프레시 토큰
    @jakarta.persistence.Column(length = 1024)
    private String token;

    // 만료 시간
    @jakarta.persistence.Column(name = "expirydate")
    private LocalDateTime expiryDate;
}
