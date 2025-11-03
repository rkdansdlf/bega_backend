package com.example.cheerboard.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity 
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, length = 10)
    private String favoriteTeamId; // 'LG','DO',...

    @Column(nullable = false)
    private String role; // USER/ADMIN
}