package com.example.demo.mypage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// * 마이페이지 프로필 정보 조회 및 수정 요청 시 사용되는 DTO
public class UserProfileDto {

    @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2자에서 20자 사이여야 합니다.")
    private String name; // 닉네임 (수정 가능)

    @Email(message = "유효하지 않은 이메일 형식입니다.")
    @NotBlank(message = "이메일은 필수입니다.")
    private String email; // 이메일 (조회 전용, 수정 불가능)
    
    // 응원 구단
    private String favoriteTeam; 
    
    // 프로필 이미지 URL
    private String profileImageUrl;
    
    // 가입일자 (조회 전용)
    private String createdAt; 
    
    private String role; 
}