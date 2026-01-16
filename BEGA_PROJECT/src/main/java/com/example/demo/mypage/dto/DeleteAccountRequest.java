package com.example.demo.mypage.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {

    // LOCAL 사용자만 비밀번호 확인 필요 (OAuth 사용자는 null 가능)
    private String password;

    // 탈퇴 확인 문구 (선택사항)
    private String confirmText;
}
