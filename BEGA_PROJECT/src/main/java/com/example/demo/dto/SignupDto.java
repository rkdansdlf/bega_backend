package com.example.demo.dto;

import com.example.demo.validation.PasswordMatches;
import com.example.demo.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@PasswordMatches  // 🔥 클래스 레벨에서 비밀번호 일치 검증
public class SignupDto {

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String name;

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "유효하지 않은 이메일 형식입니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @ValidPassword  // 🔥 비밀번호 복잡도 검증
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    private String confirmPassword;

    private String favoriteTeam;

    // 소셜 연동 관련 필드
    private String provider;
    private String providerId;

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