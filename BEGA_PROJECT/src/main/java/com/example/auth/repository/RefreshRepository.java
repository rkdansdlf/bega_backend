package com.example.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.auth.entity.RefreshToken;

import jakarta.persistence.LockModeType;
import java.util.List;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    RefreshToken findByEmail(String email);

    RefreshToken findByToken(String token);

    List<RefreshToken> findAllByEmailOrderByIdDesc(String email);

    List<RefreshToken> findAllByEmail(String email);

    List<RefreshToken> findAllByEmailAndTokenNot(String email, String token);

    List<RefreshToken> findAllByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.token = :token")
    List<RefreshToken> findAllByTokenForUpdate(@Param("token") String token);

    int deleteByEmail(String email);
}
