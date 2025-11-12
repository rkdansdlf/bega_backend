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

    // 일반 회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signUp(@Valid @RequestBody SignupDto signupDto) { 
        try {
            // 비밀번호 일치 확인
            if (!signupDto.getPassword().equals(signupDto.getConfirmPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("비밀번호와 비밀번호 확인이 일치하지 않습니다."));
            }

            // SignupRequestDto를 UserDto로 변환하여 서비스단에 전달
            UserDto userDto = signupDto.toUserDto();
            
            userService.signUp(userDto);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("회원가입이 완료되었습니다."));
                    
        } catch (IllegalArgumentException e) {
            // 이미 존재하는 이메일, 소셜 계정 연동 문제 등
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("회원가입 처리 중 오류가 발생했습니다."));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginDto request, HttpServletResponse response) {  // 🔥 HttpServletResponse 추가
        try {
            // UserService의 인증 로직 호출
            Map<String, Object> loginData = userService.authenticateAndGetToken(
                request.getEmail(), 
                request.getPassword()
            );
            
            String accessToken = (String) loginData.get("accessToken");
            
            // 🔥 JWT를 쿠키에 설정
            Cookie jwtCookie = new Cookie("Authorization", accessToken);
            jwtCookie.setHttpOnly(true);
            jwtCookie.setSecure(false);  // 개발 환경에서는 false, 프로덕션에서는 true
            jwtCookie.setPath("/");
            jwtCookie.setMaxAge(60 * 60);  // 1시간
            response.addCookie(jwtCookie);
            
            log.info("✅ 로그인 성공 및 JWT 쿠키 설정: {}", request.getEmail());
            
            // 성공 응답
            return ResponseEntity.ok(ApiResponse.success(null, loginData));

        } catch (IllegalArgumentException e) {
            log.warn("❌ 로그인 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("로그인 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
        }
    }



     // 이메일 중복 체크
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse> checkEmail(@RequestParam String email) {
        boolean exists = userService.isEmailExists(email);
        if (exists) {
            return ResponseEntity.ok(ApiResponse.error("이미 사용 중인 이메일입니다."));
        }
        return ResponseEntity.ok(ApiResponse.success("사용 가능한 이메일입니다."));
    }
    
    // 로그아웃 
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout() {
        ResponseCookie expiredCookie = ResponseCookie.from("Authorization", "") 
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0) // 쿠키 삭제
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.success("로그아웃 성공. 쿠키가 삭제되었습니다."));
    }
    
}