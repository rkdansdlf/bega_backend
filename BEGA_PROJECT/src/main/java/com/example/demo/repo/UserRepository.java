package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.UserEntity;
import java.util.Optional; // Optional 임포트

public interface UserRepository extends JpaRepository<UserEntity, Long>{

	// 기존 메서드는 유지하거나 제거 (사용하지 않을 경우)
//	 Boolean existsByUsername(String username); 
//	 UserEntity findByUsername(String username);
	 
     
    // 🚨 필수 추가: 이메일을 기반으로 존재 여부 확인 (signUp/isEmailExists에서 사용)
	 Boolean existsByEmail(String email); 
	 
	// 🚨 필수 추가: 이메일을 기반으로 사용자 조회 (로그인 및 Spring Security에서 사용)
	 Optional<UserEntity> findByEmail(String email); 
}