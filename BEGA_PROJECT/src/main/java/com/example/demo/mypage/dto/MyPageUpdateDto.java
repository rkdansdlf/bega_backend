package com.example.demo.mypage.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 프로필 정보 업데이트를 위한 DTO입니다.
 * 프론트엔드에서 Supabase에 업로드 후 받은 URL을 받습니다.
 */
@Getter
@Setter
public class MyPageUpdateDto {
    private String name;
    private String favoriteTeamId; // 응원팀 ID (예: 'SS', 'LT')
    private String profileImageUrl; // 🚨 Supabase에서 업로드 후 받은 이미지 URL
    
    // 이메일 필드는 인증 토큰에서 가져오므로 DTO에 포함하지 않아도 됩니다.
    // 하지만 만약 프론트에서 함께 보낸다면 DTO에 추가해도 무방합니다.
    // private String email; 
}