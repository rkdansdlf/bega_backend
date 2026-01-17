package com.example.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.auth.entity.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // 기존 메서드 (주석 처리)
    // Boolean existsByUsername(String username);
    // UserEntity findByUsername(String username);

    Optional<UserEntity> findByName(String name);

    Boolean existsByEmail(String email);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findByEmailContainingOrNameContaining(String email, String name);

    List<UserEntity> findByEmailContainingOrNameContainingOrderByIdAsc(String email, String name);

    List<UserEntity> findAllByOrderByIdAsc();

}