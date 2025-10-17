package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long>{

	 Boolean existsByUsername(String username);
	 
	//username을 받아 DB 테이블에서 회원을 조회하는 메소드 작성
	 UserEntity findByUsername(String username);
}
