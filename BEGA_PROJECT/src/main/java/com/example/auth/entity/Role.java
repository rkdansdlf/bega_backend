package com.example.auth.entity;

/**
 * 사용자 권한 Enum
 * 
 * ADMIN: 관리자 (모든 권한)
 * USER: 일반 사용자
 * 
 * 팀 정보는 UserEntity.favoriteTeam 필드로 관리합니다.
 */
public enum Role {
	ADMIN("ROLE_ADMIN"),
	USER("ROLE_USER");

	private final String key;

	Role(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}
}
