package com.example.demo.Oauth2;

import java.util.Collection;
import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.demo.dto.CustomOAuth2User; // CustomOAuth2User 임포트 확인
import com.example.demo.entity.RefreshToken;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.RefreshRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil; 
    private final RefreshRepository refreshRepository;

    public CustomSuccessHandler(JWTUtil jwtUtil, RefreshRepository refreshRepository) {
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
    		HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

    	System.out.println("인증 성공 객체 클래스: " + authentication.getPrincipal().getClass());
        
        String username;
        String role;

        // 1. Principal의 실제 타입 확인 및 정보 추출
        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomOAuth2User customUser) {
            // 🟢 Case 1: DB 처리가 완료된 CustomOAuth2User인 경우 (정상 플로우)
            // CustomOAuth2User에서 DB에 저장된 username과 role을 직접 가져옵니다.
            username = customUser.getUsername();
            role = customUser.getRole(); 
            
        } else if (principal instanceof OAuth2User oauth2User) {
            // 🟡 Case 2: CustomOAuth2UserService가 호출되지 않았거나, 기본 OAuth2User인 경우
            // (이 코드는 사실상 호출되지 않아야 하지만, 안전을 위해 남겨둡니다.)
            
            java.util.Map<String, Object> attributes = oauth2User.getAttributes();
            
            if (attributes == null) {
                System.err.println("오류: OAuth2User 속성(Attributes) 맵이 null입니다. (비정상 상황)");
                response.sendRedirect("/login?error=auth_failed");
                return;
            }

            // Attributes에서 email 또는 고유 name을 사용합니다. (Attributes는 DB 정보가 아님)
            username = (String) attributes.get("email"); 
            if (username == null) {
                username = oauth2User.getName(); 
            }
            
            // 권한은 Authentication 객체에서 가져옵니다.
            role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse("ROLE_USER");
            
        } else {
            // 🔴 Case 3: 예상치 못한 Principal 타입
             System.err.println("오류: 예상치 못한 Principal 타입입니다.");
             response.sendRedirect("/login?error=auth_failed");
             return;
        }

     // Access Token 만료 시간 (예: 2시간)
        long accessTokenExpiredMs = 1000 * 60 * 60 * 2L; 

        // 1-1. Access Token 생성 (기존)
        String accessToken = jwtUtil.createJwt(username, role, accessTokenExpiredMs); 
        
        // 1-2. Refresh Token 생성 (만료 시간은 JWTUtil 내부에서 7일 등으로 설정됨)
        String refreshToken = jwtUtil.createRefreshToken(username, role);

        // =============================================================
        // 🚨 2. Refresh Token DB 저장 로직 (중복 방지)
        // =============================================================
        
        // 2-1. 기존 Refresh Token이 있는지 확인
        RefreshToken existToken = refreshRepository.findByUsername(username);

        if (existToken == null) {
            // 신규 사용자 또는 첫 로그인 시
            RefreshToken newRefreshToken = new RefreshToken();
            newRefreshToken.setUsername(username);
            newRefreshToken.setToken(refreshToken);
            // 만료 시간은 JWTUtil에서 7일로 설정되었으므로, 현재 시간 + 7일로 계산하여 저장
            newRefreshToken.setExpiryDate(LocalDateTime.now().plusWeeks(1)); 
            
            refreshRepository.save(newRefreshToken);

        } else {
            // 기존 토큰이 있을 경우, 새로운 토큰으로 업데이트
            existToken.setToken(refreshToken);
            existToken.setExpiryDate(LocalDateTime.now().plusWeeks(1));
            refreshRepository.save(existToken);
        }
        
        // =============================================================
        // 🚨 3. 쿠키에 Access/Refresh Token 동시 추가
        // =============================================================
        
        // Access Token을 Authorization 쿠키에 담아 응답
        // (Access Token은 상대적으로 짧게 유지)
        response.addCookie(createCookie("Authorization", accessToken, (int)(accessTokenExpiredMs / 1000)));
        
        // Refresh Token을 Refresh 쿠키에 담아 응답
        // (Refresh Token은 길게 유지하고 HttpOnly 설정)
        // 만료 시간은 7일로 설정 (JWTUtil의 만료 시간과 일치시킴)
        response.addCookie(createCookie("Refresh", refreshToken, (int)(jwtUtil.getRefreshTokenExpirationTime() / 1000)));


        System.out.println("로그인 성공");
        System.out.println("--- JWT 토큰 발행 성공 ---");
        System.out.println("Access Token: " + accessToken.substring(0, 10) + "...");
        System.out.println("Refresh Token (DB 저장됨): " + refreshToken.substring(0, 10) + "...");
        System.out.println("토큰 사용자: " + username);
        System.out.println("권한: "+ role);
        System.out.println("--------------------------");

        
        // 4. 리디렉션
        response.sendRedirect("/"); 
    }
    
    // 🚨 createCookie 메서드 수정: 만료 시간을 인자로 받도록 수정
    private Cookie createCookie(String key, String value, int maxAgeSeconds) {

        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds); // 초 단위로 설정
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); // HTTPS 환경에서 사용 권장

        return cookie;
    }
}
        
        
        
  
 