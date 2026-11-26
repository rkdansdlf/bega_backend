package com.example.auth.dto;

import com.example.common.validation.PasswordMatches;
import com.example.common.validation.ValidPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
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
@PasswordMatches // 🔥 클래스 레벨에서 비밀번호 일치 검증
public class SignupDto {

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String name;

    @NotBlank(message = "아이디(@handle)는 필수 입력 항목입니다.")
    private String handle;

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email(message = "유효하지 않은 이메일 형식입니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @ValidPassword // 🔥 비밀번호 복잡도 검증
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    private String confirmPassword;

    private String favoriteTeam;

    @Valid
    @NotEmpty(message = "필수 정책 동의가 필요합니다.")
    private List<PolicyConsentItemDto> policyConsents;

    public UserDto toUserDto() {
        return UserDto.builder()
                .name(this.name)
                .handle(this.handle)
                .email(this.email)
                .password(this.password)
                .favoriteTeam(this.favoriteTeam)
                // 일반 회원가입은 항상 LOCAL 계정이다. provider/providerId 를 클라이언트 입력으로 받으면
                // 비로그인 요청으로 기존 계정의 로그인 제공자 정보를 바꿀 수 있다 (소셜 연동은 OAuth2 전용 절차만 허용).
                .provider("LOCAL")
                .providerId(null)
                .build();
    }
}
