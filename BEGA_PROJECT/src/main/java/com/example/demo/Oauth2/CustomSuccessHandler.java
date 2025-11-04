package com.example.demo.Oauth2;

import com.example.demo.dto.CustomOAuth2User;
import com.example.demo.entity.RefreshToken;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional; 
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository; 

    public CustomSuccessHandler(JWTUtil jwtUtil, RefreshRepository refreshRepository, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 1. Principal에서 사용자 정보 추출
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        
        // 이메일 추출을 OAuth2 Attributes와 CustomOAuth2User.getUsername() 두개를 시도
        // Google, Kakao 등 모든 제공자에 대해 안전하게 이메일을 가져오기
        String userEmail = (String) principal.getAttributes().get("email"); 
        
        if (userEmail == null || userEmail.isEmpty()) {
            // Attributes에서 이메일이 바로 추출되지 않을 경우, CustomOAuth2UserService에서 설정한 username으로 시도합니다.
            userEmail = principal.getUsername(); 
        }
        
        if (userEmail == null || userEmail.isEmpty()) {
            System.err.println("CustomSuccessHandler: Google/Kakao attributes 및 UserDto에서 유효한 email을 찾을 수 없습니다. (이메일 동의 필요 또는 데이터 누락)");
            // 이메일이 없는 경우, 에러 페이지로 리다이렉트
            getRedirectStrategy().sendRedirect(request, response, "/oauth2/login/error?message=email_missing");
            return;
        }
        
        // DB에서 UserEntity 조회
        Optional<UserEntity> userEntityOptional = userRepository.findByEmail(userEmail); 
        
        if (userEntityOptional.isEmpty()) {
            // 이메일로 사용자를 찾을 수 없는 경우
            System.err.println("CustomSuccessHandler: DB에서 이메일(" + userEmail + ")로 사용자를 찾을 수 없습니다. (DB 저장 실패 가능성)");
            // 이 경우 로그인 실패로 간주하고 에러 페이지로 리다이렉트
            getRedirectStrategy().sendRedirect(request, response, "/oauth2/login/error?message=user_not_found_in_db");
            return;
        }

        UserEntity userEntity = userEntityOptional.get();
        
        //UserEntity에서 Role 가져오기 
        String role = userEntity.getRole(); 
        
        // DB에 저장된 사용자의 이름(name)을 리다이렉션에 사용
        String userName = userEntity.getName();

        // Access Token 생성
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; // 2시간
        String accessToken = jwtUtil.createJwt(userEmail, role, accessTokenExpiredMs); 

        // Refresh Token 생성 
        String refreshToken = jwtUtil.createRefreshToken(userEmail, role); 

        // Refresh Token DB 저장 또는 업데이트
        RefreshToken existToken = refreshRepository.findByEmail(userEmail); 

        if (existToken == null) {
            RefreshToken newRefreshToken = new RefreshToken();
            newRefreshToken.setEmail(userEmail); 
            newRefreshToken.setToken(refreshToken);
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); //만료시간 1주일
            refreshRepository.save(newRefreshToken);

        } else {
            // 기존 토큰 업데이트
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }

        // 클라이언트에 토큰 응답 (쿠키)
        // Access Token 쿠키
        response.addCookie(createCookie("Authorization", accessToken, (int)(accessTokenExpiredMs / 1000)));
        
        // Refresh Token 쿠키
        int refreshTokenMaxAge = (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000); 
        response.addCookie(createCookie("Refresh", refreshToken, refreshTokenMaxAge));
        
        // 7. 리다이렉션 
        String encodedUsername = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        String redirectUrl = "http://localhost:3000";
        
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);

        System.out.println("--- JWT 토큰 발행 성공 (OAuth2 로그인) ---");
        System.out.println("발행된 Access Token: " + accessToken.substring(0, 10) + "...");
        System.out.println("Refresh Token (DB 저장됨): " + refreshToken.substring(0, 10) + "...");
        System.out.println("토큰 사용자(Email): " + userEmail); 
        System.out.println("권한: " + role);
        System.out.println("-------------------------------------");
    }
    
    // 쿠키 생성
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); // HTTPS 환경에서만 사용

        return cookie;
    }
}


