package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.UserEntity;
import java.util.Optional; 

public interface UserRepository extends JpaRepository<UserEntity, Long>{

	// 기존 메서드에 사용하던 메소드(혹시 몰라 주석처리)
//	 Boolean existsByUsername(String username); 
//	 UserEntity findByUsername(String username);
     
	 Boolean existsByEmail(String email); 
	 Optional<UserEntity> findByEmail(String email); 
}