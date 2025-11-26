package com.example.demo.controller;

import com.example.demo.dto.UserDto;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.LoginDto;
import com.example.demo.dto.SignupDto;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class APIController {

    private final UserService userService;

    /**
     * 일반 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signUp(@Valid @RequestBody SignupDto signupDto) { 
        userService.signUp(signupDto.toUserDto());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다."));
    }
    
    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @Valid @RequestBody LoginDto request, 
            HttpServletResponse response) {
        
        // UserService의 인증 로직 호출
        Map<String, Object> loginData = userService.authenticateAndGetToken(
            request.getEmail(), 
            request.getPassword()
        );
        
        String accessToken = (String) loginData.get("accessToken");
        
        // JWT를 쿠키에 설정
        Cookie jwtCookie = new Cookie("Authorization", accessToken);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true);  // 개발: false, 프로덕션: true
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(60 * 60);  // 1시간
        response.addCookie(jwtCookie);
                
        // 성공 응답
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", loginData));
    }

    /**
     * 이메일 중복 체크
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email);
        
        if (exists) {
            return ResponseEntity.ok(ApiResponse.error("이미 사용 중인 이메일입니다."));
        }
        
        return ResponseEntity.ok(ApiResponse.success("사용 가능한 이메일입니다."));
    }
    
    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout() {
        ResponseCookie expiredCookie = ResponseCookie.from("Authorization", "") 
                .httpOnly(true)
                .secure(true)  // 개발: false, 프로덕션: true
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.success("로그아웃 성공"));
    }
}