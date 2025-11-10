package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.UserEntity;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long>{
    
    // 기존 메서드 (주석 처리)
    // Boolean existsByUsername(String username);
    // UserEntity findByUsername(String username);
    
    Optional<UserEntity> findByName(String name);
    Boolean existsByEmail(String email);
    Optional<UserEntity> findByEmail(String email);

}