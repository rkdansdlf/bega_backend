package com.example.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth.entity.RefreshToken;

import java.util.List;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    RefreshToken findByEmail(String email);

    RefreshToken findByToken(String token);

    List<RefreshToken> findAllByEmailOrderByIdDesc(String email);

    List<RefreshToken> findAllByEmail(String email);
    List<RefreshToken> findAllByEmailAndTokenNot(String email, String token);

    List<RefreshToken> findAllByToken(String token);

    int deleteByEmail(String email);
}
