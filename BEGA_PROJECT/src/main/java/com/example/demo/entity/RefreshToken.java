package com.example.demo.entity;

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
@Table(name="refresh_tokens", schema = "security")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토큰 소유자를 식별하는 사용자명
    private String email; 

    // 발행된 리프레시 토큰 문자열
    private String token; 
    
    // 만료 시간 (필요시)
    private LocalDateTime expiryDate; 
    // ... getter, setter, constructor
}
