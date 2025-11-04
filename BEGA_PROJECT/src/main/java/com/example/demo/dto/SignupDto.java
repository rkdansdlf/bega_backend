package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 회원가입 요청 전용 DTO (비밀번호 일치 확인용 confirmPassword 필드 포함)
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SignupDto {

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String name;

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "유효하지 않은 이메일 형식입니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    private String confirmPassword;

    private String favoriteTeam;
    
    // 소셜 연동 관련 필드는 일반 가입 시에는 null
    private String provider;
    private String providerId;

    /**
     * DTO to UserDto 변환 (Service 계층으로 전달할 때 사용)
     * confirmPassword를 제외하고 UserDto를 생성합니다.
     */
    public UserDto toUserDto() {
        return UserDto.builder()
                .name(this.name)
                .email(this.email)
                .password(this.password)
                .favoriteTeam(this.favoriteTeam)
                .provider(this.provider)
                .providerId(this.providerId)
                .build();
    }
}

