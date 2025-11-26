package com.example.mate.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "check_in_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partyId; // 파티 ID

    @Column(nullable = false)
    private Long userId; // 사용자 ID

    @Column(nullable = false, length = 100)
    private String location; // 체크인 위치

    @Column(nullable = false)
    private LocalDateTime checkedInAt; // 체크인 시간

    @PrePersist
    protected void onCreate() {
        if (checkedInAt == null) {
            checkedInAt = LocalDateTime.now();
        }
    }
}