package com.example.auth.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.service.PasswordResetService;
import com.example.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordResetControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        PasswordResetController controller = new PasswordResetController(passwordResetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("비밀번호 확인 불일치는 confirmPassword 필드 에러로 내려온다")
    void confirmPasswordMismatchReturnsFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "valid-token",
                                  "newPassword": "Password1!",
                                  "confirmPassword": "Password2!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors.confirmPassword").value("비밀번호와 비밀번호 확인이 일치하지 않습니다."));
    }
}
