package com.example.demo.entity;

public enum Role {
		 
	 ADMIN("ROLE_ADMIN"), // 관리자 
	 
	 Role_SS("ROLE_SS"), // 삼성 라이언즈
	 Role_LT("ROLE_LT"), // 롯데 자이언츠
	 Role_LG("ROLE_LG"), // LG트윈스
	 Role_OB("ROLE_OB"), // 두산 베어스
	 Role_WO("ROLE_WO"), // 키움 히어로즈
	 Role_HH("ROLE_HH"), // 한화 이글스
	 Role_SK("ROLE_SK"), // SSG 랜더스
	 Role_NC("ROLE_NC"), // NC 다이노스
	 Role_KT("ROLE_KT"), // KT wiz
	 Role_HT("ROLE_HT"), // KIA 타이거즈
	
	 USER("ROLE_USER"); // 일반 사용자

	 private final String key;

	 Role(String key) {
		 this.key = key;
	 }

	 public String getKey() {
	     return key;
	 }
	 
	 
	 
}
