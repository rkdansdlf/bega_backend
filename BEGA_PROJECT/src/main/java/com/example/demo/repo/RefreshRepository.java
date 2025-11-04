package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.RefreshToken;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long>{

	// 특정 사용자의 RefreshToken 엔티티를 찾습니다.
    // 기존 findByUsername(String username) -> findByEmail(String email)로 변경
    RefreshToken findByEmail(String email);

    // 특정 토큰 문자열을 찾습니다. (이 토큰이 DB에 저장된 유효한 토큰인지 검증할 때 사용)
    RefreshToken findByToken(String token);
}
