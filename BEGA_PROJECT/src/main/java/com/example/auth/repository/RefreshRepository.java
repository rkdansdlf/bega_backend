package com.example.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth.entity.RefreshToken;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    RefreshToken findByEmail(String email);

    RefreshToken findByToken(String token);
}
