package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.RefreshToken;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long>{

	// 특정 사용자의 RefreshToken 엔티티를 찾습니다.
    // (보통 사용자당 하나의 유효한 Refresh Token만 가지도록 설계합니다.)
    RefreshToken findByUsername(String username);

    // 특정 토큰 문자열을 찾습니다. (이 토큰이 DB에 저장된 유효한 토큰인지 검증할 때 사용)
    RefreshToken findByToken(String token);
}
