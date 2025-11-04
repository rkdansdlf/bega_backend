package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.RefreshToken;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long>{

    RefreshToken findByEmail(String email);

    RefreshToken findByToken(String token);
}
