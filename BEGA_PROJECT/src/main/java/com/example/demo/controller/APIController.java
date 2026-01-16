package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.LoginDto;
import com.example.demo.dto.SignupDto;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
                request.getPassword());

        String accessToken = (String) loginData.get("accessToken");
        String refreshToken = (String) loginData.get("refreshToken");

        // JWT를 쿠키에 설정 (Access Token)
        Cookie jwtCookie = new Cookie("Authorization", accessToken);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(false); // 개발: false, 프로덕션: true
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(60 * 60); // 1시간
        response.addCookie(jwtCookie);

        // Refresh Token 쿠키 설정
        Cookie refreshCookie = new Cookie("Refresh", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false); // 개발: false, 프로덕션: true
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(60 * 60 * 24 * 7); // 7일
        response.addCookie(refreshCookie);

        // 성공 응답
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", loginData));
    }

    /**
     * 이메일 중복 체크
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email.trim().toLowerCase());

        if (exists) {
            return ResponseEntity.ok(ApiResponse.error("이미 사용 중인 이메일입니다."));
        }

        return ResponseEntity.ok(ApiResponse.success("사용 가능한 이메일입니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        // 1. JWT (Authorization) 및 Refresh 쿠키 추출하여 이메일 확인
        String email = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("Authorization")) {
                    try {
                        email = userService.getJWTUtil().getEmail(cookie.getValue());
                    } catch (Exception e) {
                        // 토큰 만료 등의 경우 무시
                    }
                }
            }
        }

        // 2. DB에서 리프레시 토큰 삭제
        if (email != null) {
            userService.deleteRefreshTokenByEmail(email);
        }

        // 3. Authorization 쿠키 삭제
        ResponseCookie expireAuthCookie = ResponseCookie.from("Authorization", "")
                .httpOnly(true)
                .secure(false) // local 개발 환경에서는 false
                .path("/")
                .maxAge(0)
                .build();

        // 4. Refresh 쿠키 삭제
        ResponseCookie expireRefreshCookie = ResponseCookie.from("Refresh", "")
                .httpOnly(true)
                .secure(false) // local 개발 환경에서는 false
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expireAuthCookie.toString())
                .header(HttpHeaders.SET_COOKIE, expireRefreshCookie.toString())
                .body(ApiResponse.success("로그아웃 성공"));
    }
}