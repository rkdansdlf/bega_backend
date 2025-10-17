package com.example.demo.dto;

import java.util.Map;

public class KaKaoResponse implements OAuth2Response{

    private final Map<String, Object> attribute;

    public KaKaoResponse(Map<String, Object> attribute) {

    	this.attribute = attribute;
    }

    @Override
    public String getProvider() {

        return "kakao";
    }

    @Override
    public String getProviderId() {

        return attribute.get("id").toString();
    }

    @Override
    public String getEmail() {
        // 1. kakao_account 맵을 가져옵니다.
        Map<String, Object> kakaoAccount = (Map<String, Object>) attribute.get("kakao_account");

        // 2. kakaoAccount 맵이 존재하고, 그 안에 "email" 키가 있을 때만 접근
        if (kakaoAccount != null) {
            Object email = kakaoAccount.get("email");
            
            // 3. email 값이 null이 아닐 때만 toString() 호출
            if (email != null) {
                return email.toString();
            }
        }
        
        return null; // 이메일 정보가 없으면 null 반환
    }

    @Override
    public String getName() {
        // 1. kakao_account 맵을 가져옵니다.
        Map<String, Object> kakaoAccount = (Map<String, Object>) attribute.get("kakao_account");

        if (kakaoAccount == null) {
            return "알 수 없는 사용자"; // 널 체크
        }
        
        // 2. profile 맵을 가져옵니다. (닉네임은 보통 여기에 있습니다)
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        if (profile != null) {
            // 3. nickname 값을 가져옵니다.
            Object name = profile.get("nickname");
            
            // 4. 안전하게 toString()을 호출합니다.
            if (name != null) {
                return name.toString();
            }
        }
        
        // 이름 정보가 없거나 null일 경우 대체 문자열 반환
        return "이름 없음"; 
    }
}