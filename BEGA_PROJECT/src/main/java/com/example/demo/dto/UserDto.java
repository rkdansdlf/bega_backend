package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자 정보 전달 DTO (Lombok Builder 패턴 활성화 및 Validation 적용)
 * @Data는 @Getter, @Setter, @ToString, @EqualsAndHashCode를 포함합니다.
 */
@Data 
@Builder // !!! 이 어노테이션이 findUserByEmail의 .builder() 오류를 해결합니다 !!!
@NoArgsConstructor
@AllArgsConstructor 
public class UserDto {
    
    private Long id; // DB Entity ID (Service에서 조회 시 사용)

    // 1. 사용자명/ID (프론트에서 'username'으로 전송)
    @NotBlank(message = "사용자명은 필수입니다.")
    private String username; 
    
    private String name; // 실제 이름 (소셜 로그인 Response에서 받을 수 있음)
    
    // 2. 이메일
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    // 3. 비밀번호
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
    private String password; // 로컬 로그인/회원가입 시에만 사용
    
    // 4. 응원팀 (nullable)
    private String favoriteTeam; 

    // 5. 역할 (Service에서 설정)
    private String role; 
    
    // 🚀 소셜 로그인 및 연동을 위해 추가된 필드
    private String provider; // GOOGLE, KAKAO 등
    private String providerId; // 소셜 제공자 고유 ID
}