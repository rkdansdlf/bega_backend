package com.example.auth.entity;

/**
 * 사용자 권한 Enum
 *
 * SUPER_ADMIN: 최고 관리자 (권한 관리 가능)
 * ADMIN: 관리자 (일반 관리 기능)
 * USER: 일반 사용자
 *
 * 팀 정보는 UserEntity.favoriteTeam 필드로 관리합니다.
 */
public enum Role {
	SUPER_ADMIN("ROLE_SUPER_ADMIN"),
	ADMIN("ROLE_ADMIN"),
	USER("ROLE_USER");

	private final String key;

	Role(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	/**
	 * 문자열 키에서 Role enum으로 변환
	 * @param key "ROLE_ADMIN" 형식의 문자열
	 * @return 해당 Role enum
	 * @throws IllegalArgumentException 알 수 없는 역할인 경우
	 */
	public static Role fromKey(String key) {
		for (Role role : values()) {
			if (role.key.equals(key)) {
				return role;
			}
		}
		throw new IllegalArgumentException("알 수 없는 역할입니다: " + key);
	}
}
