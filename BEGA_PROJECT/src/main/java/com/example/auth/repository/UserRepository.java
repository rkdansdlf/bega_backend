package com.example.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.auth.entity.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByHandle(String handle);

    Boolean existsByHandle(String handle);

    Optional<UserEntity> findByUniqueId(java.util.UUID uniqueId);

    Optional<UserEntity> findByName(String name);

    Boolean existsByEmail(String email);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findByEmailContainingOrNameContaining(String email, String name);

    List<UserEntity> findByEmailContainingOrNameContainingOrderByIdAsc(String email, String name);

    List<UserEntity> findAllByOrderByIdAsc();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE UserEntity u SET u.cheerPoints = COALESCE(u.cheerPoints, 0) + :points WHERE u.id = :userId")
    void modifyCheerPoints(@org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("points") int points);
}